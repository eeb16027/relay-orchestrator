# Relay: AI Workflow Orchestrator
Relay is a durable, AI-capable workflow orchestration engine built in Java
with Spring Boot and MySQL. It lets you define workflows as graphs of typed
nodes, trigger runs via webhook or API, execute steps asynchronously through
a persistent queue and worker pool, pause for human approval on sensitive
actions, validate AI outputs against a JSON Schema before letting them
propagate, and recover cleanly from crashes without duplicating side effects.

## Table of Contents
- [Setup](#setup)
- [Architecture](#architecture)
- [Run & Step State Machines](#run--step-state-machines)
- [Exactly-Once / Crash Recovery Approach](#exactly-once--crash-recovery-approach)
- [Approval Gates](#approval-gates)
- [Guardrails](#guardrails)
- [Template Resolution](#template-resolution)
- [AI Node](#ai-node)
- [Mock World](#mock-world)
- [REST API Reference](#rest-api-reference)
- [CLI Console Reference](#cli-console-reference)
- [Running the Demo Scenarios](#running-the-demo-scenarios)
- [Automated Tests](#automated-tests)
- [Known Limitations](#known-limitations)

---

## Setup

**Requirements:** Java 17+, Maven 3.8+, MySQL 8.0.1+ (needed for
`SELECT ... FOR UPDATE SKIP LOCKED` support).

1. Create the database:
   ```sql
   CREATE DATABASE relay_db;
2.Update src/main/resources/application.properties with your MySQL
username/password if different from root / root.

3.Import into an IDE (e.g. Spring Tool Suite): File → Import → Maven →
Existing Maven Projects.

4.Build:
         mvn clean install
5.Run RelayApplication as a Spring Boot App. Hibernate (ddl-auto=update)
creates all tables automatically: workflows, workflow_versions,
runs, steps, approvals.

6.The CLI console starts automatically on the same console — wait for the
relay> prompt.


## Architecture
com.relay
├── workflow/
│ ├── model/ Workflow, WorkflowVersion, Run, Step, Approval + enums
│ ├── repository/ Spring Data JPA repos, incl. SKIP LOCKED queries
│ ├── graph/ GraphParser, WorkflowGraph, NodeDefinition, EdgeDefinition
│ ├── service/ WorkflowService, RunService, ApprovalService
│ ├── dto/ Request/response DTOs
│ └── exception/ Custom exceptions + GlobalExceptionHandler
├── trigger/ TriggerController (webhook + manual trigger)
├── engine/
│ ├── executor/ HttpRequestNodeExecutor, ConditionNodeExecutor,
│ │ DelayNodeExecutor, NotifyNodeExecutor
│ ├── NodeExecutor, NodeExecutorRegistry, NodeExecutionContext/Result
│ ├── StepClaimService, StepResultPersister, StepExecutionService
│ ├── WorkflowEngineWorker (the polling worker thread pool)
│ ├── RunContextBuilder, TemplateResolver
│ └── CrashRecoveryService
├── ai/
│ ├── AiProvider, AiCompletionRequest/Response
│ ├── MockAiProvider, RealAiProvider
│ ├── JsonSchemaValidator, PromptInjectionGuard
│ └── AiNodeExecutor
├── mockworld/ MockWorldController (self-contained fake external system)
└── cli/
    ├── RelayCliRunner, CliCommandHandler
    ├── DemoScenarioRunner (online, calls httpbin.org)
    └── OfflineDemoScenarioRunner (offline, calls mock world)
## Core design principle:
the engine never "runs" continuously. Every
worker thread does one atomic cycle — claim one pending step, execute it,
persist the result, then look for the next one. This is the entire basis
for durability: a crash simply means the loop restarts and finds work
waiting exactly where it left off.

Node types are pluggable. Every deterministic node type (HTTP_REQUEST,
CONDITION, DELAY, NOTIFY) and the AI node implement the same
NodeExecutor interface, auto-discovered by Spring via
NodeExecutorRegistry. Adding a new node type means writing one new class —
zero changes to the engine itself.


## Run & Step State Machines
Workflow: DRAFT --publish--> PUBLISHED
           (publish validates the graph, freezes an immutable WorkflowVersion)

Run: RUNNING --> COMPLETED (no outgoing edge left)
           RUNNING --> FAILED (step cap exceeded / node error / rejected approval)
           RUNNING --> WAITING_APPROVAL --(approve)--> RUNNING
                                        --(reject)---> FAILED

Step: PENDING --(claimed)--> RUNNING --> COMPLETED
                                          --> FAILED
                                          --> WAITING_APPROVAL (sensitive node, no approval yet)

## Exactly-Once / Crash Recovery Approach
Every step that performs a side effect is assigned a unique
idempotencyKey (a UUID) at creation time.
HttpRequestNodeExecutor sends this key as an Idempotency-Key HTTP
header on every outbound call.
MockWorldController demonstrates the receiving side: it tracks orders by
idempotency key and, on a repeat key, returns the original order without
reprocessing — proving duplicate side effects are prevented, not just
assumed to be.
On application startup, CrashRecoveryService scans for any Step left
in RUNNING status — the fingerprint of a worker that died mid-execution
— and resets it to PENDING. The worker pool picks it up again
automatically; the idempotency key ensures any side effect that already
fired isn't duplicated on retry.
Verified manually: trigger a run containing a DELAY node, kill the
application mid-step, restart it, and confirm the run resumes and
completes without duplicating any external call. See the CLI walkthrough
below.


## Approval Gates
Any node can require human approval by setting "requiresApproval": true
in its config (APPROVAL-type nodes always require it implicitly). Before
dispatching to any executor, StepExecutionService checks the database
for an APPROVED Approval record for that step — and if none exists, it
pauses the run instead of executing.
This check happens in code, against persisted state, never against
anything an AI model outputs or claims — a hallucinated or manipulated
instruction cannot cause a sensitive action to execute without a real,
human-created approval row.
Approve: POST /api/approvals/{id}/approve
Reject: POST /api/approvals/{id}/reject
Rejecting fails the step and the run immediately — a rejected sensitive
action is never retried automatically.

## Guardrails
Step cap — Run.maxSteps (default 100, configurable via
relay.engine.run.default-max-steps). Checked in StepResultPersister
after every successful step; a runaway/looping workflow is halted and
the run marked FAILED rather than looping forever.
Idempotency keys — see above.
Timeouts — every outbound HTTP call (relay.engine.http.timeout-ms)
and every AI provider call (relay.engine.ai.timeout-ms) has a
configured timeout.
Prompt injection — PromptInjectionGuard does best-effort
text-level neutralization of known injection phrases (e.g. "ignore
previous instructions") before any text reaches the AI provider. This is
explicitly not the primary defense — regex filtering can be evaded.
The real, structural protection is that approval gates and the step cap
are enforced against database state, never against anything the model
outputs, so even content that fully evades this filter cannot bypass an
approval gate or exceed the step cap.

##Template Resolution
Node config values can reference the run's trigger payload and prior
steps' outputs using {{path}} syntax, resolved against:
{ "trigger": <original trigger payload>, "steps": { "<nodeId>": { "output": <that step's output> } } }

Exact-match placeholders (e.g. "url": "{{trigger.orderId}}") resolve
to the real JSON type at that path (string, number, object).
Embedded placeholders in freeform text (e.g. an AI node's
promptTemplate) are interpolated as strings, in place, mid-sentence.
Resolved config is persisted back onto the Step row before execution, so
the trace always shows real substituted values, never raw {{...}} text.


###AI Node
Config shape:
{
  "promptTemplate": "Classify this order: {{trigger.orderId}}",
  "outputSchema": { "type": "object", "properties": { ... }, "required": [...] },
  "mockResponse": { ... }
}



## AiProvider is a thin adapter interface with two implementations:
MockAiProvider (default, relay.ai.provider=mock) and RealAiProvider
(calls the Anthropic Messages API, relay.ai.provider=real). This keeps
the AI provider swappable and fully mockable in engine tests without any
network dependency.
The model's raw output is validated against outputSchema (JSON Schema
draft 2020-12, via JsonSchemaValidator) before the engine advances
to any downstream node. A malformed or hallucinated shape throws, which
the engine turns into a FAILED step rather than silently propagating
bad data.
Prompt text is passed through PromptInjectionGuard before reaching the
provider.
Token usage (promptTokens / completionTokens) is recorded on the
Step row for every AI node execution.

##Mock World:
MockWorldController (/mock-world/**) is a small, self-contained fake
external system so demos never depend on internet access or a third-party
site. It exposes:
POST /mock-world/orders — accepts an Idempotency-Key header; returns
duplicate: false for a new key, duplicate: true (same orderId) for
a repeated key.
GET /mock-world/orders/{orderId} — look up an order.
DELETE /mock-world/reset — clears in-memory state for a clean re-run.
GET /mock-world/stats — call counts, for verification.
State is in-memory only and resets on app restart or via /reset.


##REST API Reference:
Method        Path                             Purpose
POST          /api/workflows                   Create draft workflow
GET           /api/workflows                   List workflows
GET           /api/workflows/{id}              Get workflow
PUT           /api/workflows/{id}/draft        Update draft definition
POST          /api/workflows/{id}/publish      Validate + freeze a new version
GET           /api/workflows/{id}/versions     List published versions
POST          /api/workflows/{id}/trigger      Manual trigger (JSON body: {"payload": {...}})
POST          /api/workflows/{id}/webhook      Webhook trigger (requires X-Relay-Signature header matching the workflow's secret)
GET           /api/approvals                   List all approvals
GET           /api/approvals/{id}              Get one approval
POST          /api/approvals/{id}/approve      Approve a pending gate
POST          /api/approvals/{id}/reject       Reject a pending gate
POST          /mock-world/orders               Mock external side-effect endpoint



##CLI Console Reference:
help
list-workflows
show-workflow <workflowId>
create-workflow <name> <definitionJson>
update-draft <workflowId> <definitionJson>
publish <workflowId>
trigger <workflowId> <payloadJson>
status <runId>
trace <runId>
list-approvals
approve <approvalId> [approver]
reject <approvalId> [approver]
demo # runs 9 scenarios online (calls httpbin.org)
demo-offline # runs 9 scenarios fully offline (calls the mock world)
exit

The CLI calls the exact same service layer as the REST controllers — it is
not a separate mocked pathway, it exercises real application logic.



##Running the Demo Scenarios:
demo-offline (recommended — no internet dependency) runs, in order:
Simple NOTIFY happy path
CONDITION branching, both directions
Approval gate — approved
Approval gate — rejected
AI node — schema-valid mock response
AI node — schema-invalid mock response (correctly fails)
Step-cap guardrail against an intentional infinite loop
Template resolution proof (resolved values, not raw {{...}})
Idempotency dedup proof — calls the mock world twice with the same key,
confirms the second call is recognized as a duplicate
demo runs the same nine scenarios but calls httpbin.org instead of the
mock world for the HTTP-dependent ones — requires internet access.
For a live crash-recovery demonstration: trigger a workflow containing a
DELAY node, kill the running application mid-delay, restart it, and
observe CrashRecoveryService reset the stuck step and the run complete
on its own.


##Automated Tests:
Located under src/test/java, mirroring the package structure of the
classes under test:
GraphParserTest — publish-time structural validation (dangling edges,
unreachable nodes, missing entry node, duplicate IDs, malformed JSON)
TemplateResolverTest — exact-match and embedded template resolution
JsonSchemaValidatorTest — AI output schema enforcement
PromptInjectionGuardTest — injection-phrase neutralization
AiNodeExecutorTest — AI node end-to-end (mocked provider), including
schema-validation failure
StepResultPersisterTest — step-cap guardrail, idempotency-key
uniqueness per created step
StepExecutionServiceTest — approval-gate enforcement before dispatch,
empty-queue handling, normal execution path


##Known Limitations:
The webhook trigger's signature check (X-Relay-Signature header) is
only exercisable via real HTTP (curl/Postman/MockMvc) — not the CLI,
since it's specifically designed to simulate an external caller.
DelayNodeExecutor blocks the worker thread for its configured duration
rather than rescheduling — acceptable at demo scale, not
production-grade (a production version would persist a runAfter
timestamp and free the thread in the meantime).
RunContextBuilder re-reads all completed steps for a run on every node
execution rather than caching — simple and correct, not optimized for
very long-running workflows.
Full integration paths (a real webhook HTTP call, the live
kill-and-resume demo, multi-node runs end-to-end) are verified by
manually running the app per the CLI walkthroughs above, not by
automated integration tests — the automated suite covers unit-level
logic for every criterion named in the assessment brief.
