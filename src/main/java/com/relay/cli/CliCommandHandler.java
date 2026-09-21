package com.relay.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.workflow.model.Approval;
import com.relay.workflow.model.Run;
import com.relay.workflow.model.Step;
import com.relay.workflow.model.Workflow;
import com.relay.workflow.model.WorkflowVersion;
import com.relay.workflow.repository.RunRepository;
import com.relay.workflow.repository.StepRepository;
import com.relay.workflow.service.ApprovalService;
import com.relay.workflow.service.RunService;
import com.relay.workflow.service.WorkflowService;
import org.springframework.stereotype.Component;

@Component
public class CliCommandHandler {

	private final WorkflowService workflowService;
	private final RunService runService;
	private final ApprovalService approvalService;
	private final RunRepository runRepository;
	private final StepRepository stepRepository;
	private final ObjectMapper objectMapper;
	private final DemoScenarioRunner demoScenarioRunner;
	private final OfflineDemoScenarioRunner offlineDemoScenarioRunner;

	public CliCommandHandler(WorkflowService workflowService, RunService runService, ApprovalService approvalService,
			RunRepository runRepository, StepRepository stepRepository, ObjectMapper objectMapper,
			DemoScenarioRunner demoScenarioRunner, OfflineDemoScenarioRunner offlineDemoScenarioRunner) {
		this.workflowService = workflowService;
		this.runService = runService;
		this.approvalService = approvalService;
		this.runRepository = runRepository;
		this.stepRepository = stepRepository;
		this.objectMapper = objectMapper;
		this.demoScenarioRunner = demoScenarioRunner;
		this.offlineDemoScenarioRunner = offlineDemoScenarioRunner;
	}

	public void handle(String line) throws Exception {
		// limit=3: [command, firstArg, everythingElse] - keeps JSON
		// arguments (which contain spaces) intact as a single token.
		String[] parts = line.split("\\s+", 3);
		String command = parts[0].toLowerCase();

		switch (command) {
		case "help" -> printHelp();
		case "list-workflows" -> listWorkflows();
		case "show-workflow" -> showWorkflow(parts);
		case "create-workflow" -> createWorkflow(parts);
		case "update-draft" -> updateDraft(parts);
		case "publish" -> publish(parts);
		case "trigger" -> trigger(parts);
		case "status" -> status(parts);
		case "trace" -> trace(parts);
		case "list-approvals" -> listApprovals();
		case "approve" -> decide(parts, true);
		case "reject" -> decide(parts, false);
		case "demo" -> demoScenarioRunner.runAll();
		case "demo-offline" -> offlineDemoScenarioRunner.runAll();
		default -> System.out.println("Unknown command '" + command + "'. Type 'help'.");
		}
	}

	private void printHelp() {
		System.out.println(
				"""
						Commands:
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
						  exit

						Example - single-node NOTIFY workflow:
						  create-workflow demo {"entryNodeId":"n1","nodes":[{"id":"n1","type":"NOTIFY","config":{"message":"hi","channel":"ops"}}],"edges":[]}
						  publish 1
						  trigger 1 {"orderId":"12345"}
						  trace 1
						""");
	}

	private void listWorkflows() {
		var workflows = workflowService.listWorkflows();
		if (workflows.isEmpty()) {
			System.out.println("(no workflows yet)");
			return;
		}
		for (Workflow w : workflows) {
			System.out.printf("id=%d name=%-20s status=%-10s publishedVersionId=%s%n", w.getId(), w.getName(),
					w.getStatus(), w.getPublishedVersionId());
		}
	}

	private void showWorkflow(String[] parts) {
		Long id = requireLong(parts, 1, "workflowId");
		Workflow w = workflowService.getWorkflowOrThrow(id);
		System.out.println("id: " + w.getId());
		System.out.println("name: " + w.getName());
		System.out.println("status: " + w.getStatus());
		System.out.println("webhookSecret: " + w.getWebhookSecret());
		System.out.println("draftDefinition: " + w.getDraftDefinition());
	}

	private void createWorkflow(String[] parts) throws Exception {
		requireArgs(parts, 3, "create-workflow <name> <definitionJson>");
		String name = parts[1];
		String definitionJson = parts[2];
		Workflow w = workflowService.createDraft(name, definitionJson);
		System.out.println("Created workflow id=" + w.getId() + " (status=" + w.getStatus() + ")");
		System.out.println("webhookSecret: " + w.getWebhookSecret());
	}

	private void updateDraft(String[] parts) {
		requireArgs(parts, 3, "update-draft <workflowId> <definitionJson>");
		Long id = requireLong(parts, 1, "workflowId");
		Workflow w = workflowService.updateDraftDefinition(id, parts[2]);
		System.out.println("Updated draft for workflow " + w.getId());
	}

	private void publish(String[] parts) {
		Long id = requireLong(parts, 1, "workflowId");
		WorkflowVersion version = workflowService.publish(id);
		System.out.println("Published workflow " + id + " as version " + version.getVersionNumber() + " (versionId="
				+ version.getId() + ")");
	}

	private void trigger(String[] parts) throws Exception {
		requireArgs(parts, 3, "trigger <workflowId> <payloadJson>");
		Long id = requireLong(parts, 1, "workflowId");
		JsonNode payload = objectMapper.readTree(parts[2]);
		if (!payload.isObject()) {
			throw new IllegalArgumentException("payloadJson must be a JSON object, e.g. {\"orderId\":\"123\"}");
		}
		Run run = runService.startRun(id, (ObjectNode) payload);
		System.out.println("Started run id=" + run.getId() + " status=" + run.getStatus());
	}

	private void status(String[] parts) {
		Long runId = requireLong(parts, 1, "runId");
		Run run = runService.getRunOrThrow(runId);
		System.out.println("runId: " + run.getId());
		System.out.println("workflowId: " + run.getWorkflowId());
		System.out.println("status: " + run.getStatus());
		System.out.println("stepsExecuted: " + run.getStepsExecuted() + " / " + run.getMaxSteps());
		System.out.println("failureReason: " + run.getFailureReason());
	}

	private void trace(String[] parts) {
		Long runId = requireLong(parts, 1, "runId");
		var steps = stepRepository.findByRunIdOrderBySequenceIndexAsc(runId);
		if (steps.isEmpty()) {
			System.out.println("(no steps found for run " + runId + ")");
			return;
		}
		for (Step s : steps) {
			System.out.println("---");
			System.out.printf("seq=%d node=%s type=%s status=%s attempts=%d%n", s.getSequenceIndex(), s.getNodeId(),
					s.getNodeType(), s.getStatus(), s.getAttemptCount());
			System.out.println("resolvedInput: " + truncate(s.getResolvedInput()));
			System.out.println("output: " + truncate(s.getOutput()));
			if (s.getErrorMessage() != null) {
				System.out.println("error: " + s.getErrorMessage());
			}
			System.out.println("idempotencyKey: " + s.getIdempotencyKey());
			System.out.println("started=" + s.getStartedAt() + " completed=" + s.getCompletedAt());
		}
	}

	private void listApprovals() {
		var approvals = approvalService.listAll();
		if (approvals.isEmpty()) {
			System.out.println("(no approvals)");
			return;
		}
		for (Approval a : approvals) {
			System.out.printf("approvalId=%d runId=%d stepId=%d status=%s approver=%s%n", a.getId(), a.getRunId(),
					a.getStepId(), a.getStatus(), a.getApprover());
		}
	}

	private void decide(String[] parts, boolean approve) {
		Long approvalId = requireLong(parts, 1, "approvalId");
		String approver = parts.length > 2 ? parts[2] : null;
		Approval result = approve ? approvalService.approve(approvalId, approver)
				: approvalService.reject(approvalId, approver);
		System.out.println((approve ? "Approved" : "Rejected") + " approval " + result.getId() + " -> status="
				+ result.getStatus());
	}

	private String truncate(String s) {
		if (s == null)
			return "null";
		return s.length() > 200 ? s.substring(0, 200) + "...(truncated)" : s;
	}

	private Long requireLong(String[] parts, int index, String argName) {
		if (parts.length <= index) {
			throw new IllegalArgumentException("Missing required argument: " + argName);
		}
		try {
			return Long.parseLong(parts[index]);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(argName + " must be a number, got: " + parts[index]);
		}
	}

	private void requireArgs(String[] parts, int minLength, String usage) {
		if (parts.length < minLength) {
			throw new IllegalArgumentException("Usage: " + usage);
		}
	}
}
