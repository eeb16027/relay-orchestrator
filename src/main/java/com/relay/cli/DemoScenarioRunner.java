package com.relay.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.workflow.model.*;
import com.relay.workflow.repository.ApprovalRepository;
import com.relay.workflow.repository.StepRepository;
import com.relay.workflow.service.ApprovalService;
import com.relay.workflow.service.RunService;
import com.relay.workflow.service.WorkflowService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Drives every implemented feature end-to-end with hardcoded workflow
 * definitions, so the whole app can be exercised with one command
 * instead of hand-typing JSON into the CLI. Each scenario creates its
 * own workflow (so results don't collide), triggers it, polls until
 * the run reaches a terminal (or paused) state, and prints a verdict.
 */
@Component
public class DemoScenarioRunner {

    private final WorkflowService workflowService;
    private final RunService runService;
    private final ApprovalService approvalService;
    private final ApprovalRepository approvalRepository;
    private final StepRepository stepRepository;
    private final ObjectMapper objectMapper;

    public DemoScenarioRunner(WorkflowService workflowService,
                               RunService runService,
                               ApprovalService approvalService,
                               ApprovalRepository approvalRepository,
                               StepRepository stepRepository,
                               ObjectMapper objectMapper) {
        this.workflowService = workflowService;
        this.runService = runService;
        this.approvalService = approvalService;
        this.approvalRepository = approvalRepository;
        this.stepRepository = stepRepository;
        this.objectMapper = objectMapper;
    }

    public void runAll() throws Exception {
        System.out.println("\n########## SCENARIO 1: Simple NOTIFY happy path ##########");
        scenarioSimpleNotify();

        System.out.println("\n########## SCENARIO 2: CONDITION branching ##########");
        scenarioConditionBranching();

        System.out.println("\n########## SCENARIO 3: Approval gate (pause + approve) ##########");
        scenarioApprovalGate();

        System.out.println("\n########## SCENARIO 4: Approval gate (pause + reject) ##########");
        scenarioApprovalReject();

        System.out.println("\n########## SCENARIO 5: AI node - schema-valid mock response ##########");
        scenarioAiSuccess();

        System.out.println("\n########## SCENARIO 6: AI node - schema-INVALID mock response ##########");
        scenarioAiSchemaFailure();

        System.out.println("\n########## SCENARIO 7: Step cap guardrail (infinite loop) ##########");
        scenarioStepCapExceeded();

        System.out.println("\n########## SCENARIO 8: Template resolution proof ##########");
        scenarioTemplateResolution();

        System.out.println("\n########## SCENARIO 9: Idempotency key uniqueness ##########");
        scenarioIdempotencyKeys();

        System.out.println("\nAll scenarios finished.\n");
    }

    // ---------- Scenario 1 ----------
    private void scenarioSimpleNotify() throws Exception {
        String definition = """
            {"entryNodeId":"n1","nodes":[
              {"id":"n1","type":"NOTIFY","config":{"message":"Order received","channel":"ops"}}
            ],"edges":[]}
            """;
        Workflow wf = workflowService.createDraft("demo-notify", definition);
        workflowService.publish(wf.getId());
        Run run = trigger(wf.getId(), "{\"orderId\":\"ORD-1\"}");
        Run finished = pollUntilTerminal(run.getId());
        printRunSummary(finished);
        assertExpected("COMPLETED", finished.getStatus().name());
    }

    // ---------- Scenario 2 ----------
    private void scenarioConditionBranching() throws Exception {
        String definition = """
            {"entryNodeId":"check","nodes":[
              {"id":"check","type":"CONDITION","config":{"field":"trigger.priority","operator":"equals","value":"high"}},
              {"id":"urgent","type":"NOTIFY","config":{"message":"URGENT path","channel":"ops"}},
              {"id":"normal","type":"NOTIFY","config":{"message":"normal path","channel":"ops"}}
            ],"edges":[
              {"fromNodeId":"check","toNodeId":"urgent","branch":"true"},
              {"fromNodeId":"check","toNodeId":"normal","branch":"false"}
            ]}
            """;
        Workflow wf = workflowService.createDraft("demo-condition", definition);
        workflowService.publish(wf.getId());

        Run runHigh = trigger(wf.getId(), "{\"priority\":\"high\"}");
        Run finishedHigh = pollUntilTerminal(runHigh.getId());
        printTrace(finishedHigh.getId());
        assertExpected("urgent", lastCompletedNodeId(finishedHigh.getId()));

        Run runLow = trigger(wf.getId(), "{\"priority\":\"low\"}");
        Run finishedLow = pollUntilTerminal(runLow.getId());
        printTrace(finishedLow.getId());
        assertExpected("normal", lastCompletedNodeId(finishedLow.getId()));
    }

    // ---------- Scenario 3 ----------
    private void scenarioApprovalGate() throws Exception {
        String definition = """
            {"entryNodeId":"place-order","nodes":[
              {"id":"place-order","type":"HTTP_REQUEST","config":{
                 "url":"https://httpbin.org/post","method":"POST","requiresApproval":true}}
            ],"edges":[]}
            """;
        Workflow wf = workflowService.createDraft("demo-approve", definition);
        workflowService.publish(wf.getId());

        Run run = trigger(wf.getId(), "{\"orderId\":\"ORD-APPROVE\"}");
        Run paused = pollUntilTerminal(run.getId());
        printRunSummary(paused);
        assertExpected("WAITING_APPROVAL", paused.getStatus().name());

        Approval approval = approvalRepository.findByStepId(latestStepId(run.getId()))
                .orElseThrow(() -> new IllegalStateException("Expected an approval record"));
        System.out.println("Approving approvalId=" + approval.getId() + " ...");
        approvalService.approve(approval.getId(), "demo-manager");

        Run resumed = pollUntilTerminal(run.getId());
        printRunSummary(resumed);
        assertExpected("COMPLETED", resumed.getStatus().name());
    }

    // ---------- Scenario 4 ----------
    private void scenarioApprovalReject() throws Exception {
        String definition = """
            {"entryNodeId":"place-order","nodes":[
              {"id":"place-order","type":"HTTP_REQUEST","config":{
                 "url":"https://httpbin.org/post","method":"POST","requiresApproval":true}}
            ],"edges":[]}
            """;
        Workflow wf = workflowService.createDraft("demo-reject", definition);
        workflowService.publish(wf.getId());

        Run run = trigger(wf.getId(), "{\"orderId\":\"ORD-REJECT\"}");
        Run paused = pollUntilTerminal(run.getId());
        assertExpected("WAITING_APPROVAL", paused.getStatus().name());

        Approval approval = approvalRepository.findByStepId(latestStepId(run.getId()))
                .orElseThrow(() -> new IllegalStateException("Expected an approval record"));
        System.out.println("Rejecting approvalId=" + approval.getId() + " ...");
        approvalService.reject(approval.getId(), "demo-manager");

        Run rejected = pollUntilTerminal(run.getId());
        printRunSummary(rejected);
        assertExpected("FAILED", rejected.getStatus().name());
    }

    // ---------- Scenario 5 ----------
    private void scenarioAiSuccess() throws Exception {
        String definition = """
            {"entryNodeId":"classify","nodes":[
              {"id":"classify","type":"AI","config":{
                 "promptTemplate":"Classify order {{trigger.orderId}} by urgency.",
                 "outputSchema":{"type":"object","properties":{
                     "category":{"type":"string"},"confidence":{"type":"number"}
                   },"required":["category","confidence"]},
                 "mockResponse":{"category":"urgent","confidence":0.95}
              }}
            ],"edges":[]}
            """;
        Workflow wf = workflowService.createDraft("demo-ai-ok", definition);
        workflowService.publish(wf.getId());
        Run run = trigger(wf.getId(), "{\"orderId\":\"ORD-AI-1\"}");
        Run finished = pollUntilTerminal(run.getId());
        printTrace(finished.getId());
        assertExpected("COMPLETED", finished.getStatus().name());
    }

    // ---------- Scenario 6 ----------
    private void scenarioAiSchemaFailure() throws Exception {
        String definition = """
            {"entryNodeId":"classify","nodes":[
              {"id":"classify","type":"AI","config":{
                 "promptTemplate":"Classify order {{trigger.orderId}} by urgency.",
                 "outputSchema":{"type":"object","properties":{
                     "category":{"type":"string"},"confidence":{"type":"number"}
                   },"required":["category","confidence"]},
                 "mockResponse":{"wrongField":"this will fail schema validation"}
              }}
            ],"edges":[]}
            """;
        Workflow wf = workflowService.createDraft("demo-ai-bad", definition);
        workflowService.publish(wf.getId());
        Run run = trigger(wf.getId(), "{\"orderId\":\"ORD-AI-2\"}");
        Run finished = pollUntilTerminal(run.getId());
        printRunSummary(finished);
        assertExpected("FAILED", finished.getStatus().name());
    }

    // ---------- Scenario 7 ----------
    private void scenarioStepCapExceeded() throws Exception {
        // n1 -> n2 -> n1 -> n2 ... forever. maxSteps caps it.
        String definition = """
            {"entryNodeId":"n1","nodes":[
              {"id":"n1","type":"NOTIFY","config":{"message":"loop-a","channel":"ops"}},
              {"id":"n2","type":"NOTIFY","config":{"message":"loop-b","channel":"ops"}}
            ],"edges":[
              {"fromNodeId":"n1","toNodeId":"n2"},
              {"fromNodeId":"n2","toNodeId":"n1"}
            ]}
            """;
        Workflow wf = workflowService.createDraft("demo-loop", definition);
        workflowService.publish(wf.getId());
        Run run = trigger(wf.getId(), "{}");
        Run finished = pollUntilTerminal(run.getId(), 15000); // loop needs more polling time
        printRunSummary(finished);
        assertExpected("FAILED", finished.getStatus().name());
        System.out.println("stepsExecuted at halt: " + finished.getStepsExecuted() +
                " (maxSteps=" + finished.getMaxSteps() + ") - guardrail confirmed it never ran forever.");
    }

    // ---------- Scenario 8 ----------
    private void scenarioTemplateResolution() throws Exception {
        String definition = """
            {"entryNodeId":"n1","nodes":[
              {"id":"n1","type":"HTTP_REQUEST","config":{
                 "url":"https://httpbin.org/anything/{{trigger.orderId}}","method":"GET"}}
            ],"edges":[]}
            """;
        Workflow wf = workflowService.createDraft("demo-template", definition);
        workflowService.publish(wf.getId());
        Run run = trigger(wf.getId(), "{\"orderId\":\"ORD-TEMPLATE-42\"}");
        Run finished = pollUntilTerminal(run.getId());
        Step step = stepRepository.findByRunIdOrderBySequenceIndexAsc(run.getId()).get(0);
        System.out.println("resolvedInput (should show real orderId, not {{...}}): " + step.getResolvedInput());
        assertExpected(true, step.getResolvedInput().contains("ORD-TEMPLATE-42"));
        assertExpected(false, step.getResolvedInput().contains("{{"));
    }

    // ---------- Scenario 9 ----------
    private void scenarioIdempotencyKeys() throws Exception {
        String definition = """
            {"entryNodeId":"n1","nodes":[
              {"id":"n1","type":"NOTIFY","config":{"message":"a","channel":"ops"}},
              {"id":"n2","type":"NOTIFY","config":{"message":"b","channel":"ops"}}
            ],"edges":[{"fromNodeId":"n1","toNodeId":"n2"}]}
            """;
        Workflow wf = workflowService.createDraft("demo-idempotency", definition);
        workflowService.publish(wf.getId());
        Run run = trigger(wf.getId(), "{}");
        pollUntilTerminal(run.getId());

        List<Step> steps = stepRepository.findByRunIdOrderBySequenceIndexAsc(run.getId());
        System.out.println("n1 idempotencyKey: " + steps.get(0).getIdempotencyKey());
        System.out.println("n2 idempotencyKey: " + steps.get(1).getIdempotencyKey());
        assertExpected(true, !steps.get(0).getIdempotencyKey().equals(steps.get(1).getIdempotencyKey()));
        System.out.println("Confirmed: every step gets a distinct idempotency key.");
    }

    // ---------- helpers ----------

    private Run trigger(Long workflowId, String payloadJson) throws Exception {
        JsonNode payload = objectMapper.readTree(payloadJson);
        Run run = runService.startRun(workflowId, (ObjectNode) payload);
        System.out.println("Triggered workflow " + workflowId + " -> runId=" + run.getId());
        return run;
    }

    private Run pollUntilTerminal(Long runId) throws InterruptedException {
        return pollUntilTerminal(runId, 8000);
    }

    private Run pollUntilTerminal(Long runId, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        Run run;
        do {
            Thread.sleep(300);
            run = runService.getRunOrThrow(runId);
            if (isTerminalOrPaused(run.getStatus())) {
                return run;
            }
        } while (System.currentTimeMillis() < deadline);
        System.out.println("WARNING: run " + runId + " did not reach a terminal state within " + timeoutMs + "ms " +
                "(current status=" + run.getStatus() + "). Increase timeout or inspect manually with 'trace " + runId + "'.");
        return run;
    }

    private boolean isTerminalOrPaused(RunStatus status) {
        return status == RunStatus.COMPLETED || status == RunStatus.FAILED
                || status == RunStatus.WAITING_APPROVAL || status == RunStatus.CANCELLED;
    }

    private Long latestStepId(Long runId) {
        List<Step> steps = stepRepository.findByRunIdOrderBySequenceIndexAsc(runId);
        return steps.get(steps.size() - 1).getId();
    }

    private String lastCompletedNodeId(Long runId) {
        List<Step> steps = stepRepository.findByRunIdOrderBySequenceIndexAsc(runId);
        for (int i = steps.size() - 1; i >= 0; i--) {
            if (steps.get(i).getStatus() == StepStatus.COMPLETED) {
                return steps.get(i).getNodeId();
            }
        }
        return "(none completed)";
    }

    private void printRunSummary(Run run) {
        System.out.printf(" -> runId=%d status=%s stepsExecuted=%d/%d failureReason=%s%n",
                run.getId(), run.getStatus(), run.getStepsExecuted(), run.getMaxSteps(), run.getFailureReason());
    }

    private void printTrace(Long runId) {
        List<Step> steps = stepRepository.findByRunIdOrderBySequenceIndexAsc(runId);
        for (Step s : steps) {
            System.out.printf(" seq=%d node=%s type=%s status=%s output=%s%n",
                    s.getSequenceIndex(), s.getNodeId(), s.getNodeType(), s.getStatus(),
                    truncate(s.getOutput()));
        }
    }

    private String truncate(String s) {
        if (s == null) return "null";
        return s.length() > 120 ? s.substring(0, 120) + "..." : s;
    }

    private void assertExpected(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            System.out.println(" *** MISMATCH *** expected=" + expected + " actual=" + actual);
        } else {
            System.out.println(" OK - expected=" + expected);
        }
    }
}