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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Same purpose as DemoScenarioRunner (exercise every implemented
 * feature end-to-end via one CLI command) but 100% offline-safe: every
 * scenario that needs an HTTP call targets MockWorldController on
 * localhost instead of a third-party site. Kept as a fully separate
 * class - not a subclass or wrapper of DemoScenarioRunner - so both
 * can be run independently to compare behavior with and without real
 * internet access reachable.
 */
@Component
public class OfflineDemoScenarioRunner {

    private final WorkflowService workflowService;
    private final RunService runService;
    private final ApprovalService approvalService;
    private final ApprovalRepository approvalRepository;
    private final StepRepository stepRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String mockWorldBaseUrl;

    public OfflineDemoScenarioRunner(WorkflowService workflowService,
                                      RunService runService,
                                      ApprovalService approvalService,
                                      ApprovalRepository approvalRepository,
                                      StepRepository stepRepository,
                                      ObjectMapper objectMapper,
                                      @Value("${server.port:8080}") String serverPort) {
        this.workflowService = workflowService;
        this.runService = runService;
        this.approvalService = approvalService;
        this.approvalRepository = approvalRepository;
        this.stepRepository = stepRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
        this.mockWorldBaseUrl = "http://localhost:" + serverPort + "/mock-world";
    }

    public void runAll() throws Exception {
        System.out.println("\n########## [OFFLINE] SCENARIO 1: Simple NOTIFY happy path ##########");
        scenarioSimpleNotify();

        System.out.println("\n########## [OFFLINE] SCENARIO 2: CONDITION branching ##########");
        scenarioConditionBranching();

        System.out.println("\n########## [OFFLINE] SCENARIO 3: Approval gate against mock world (approve) ##########");
        scenarioApprovalGateApprove();

        System.out.println("\n########## [OFFLINE] SCENARIO 4: Approval gate against mock world (reject) ##########");
        scenarioApprovalGateReject();

        System.out.println("\n########## [OFFLINE] SCENARIO 5: AI node - schema-valid mock response ##########");
        scenarioAiSuccess();

        System.out.println("\n########## [OFFLINE] SCENARIO 6: AI node - schema-INVALID mock response ##########");
        scenarioAiSchemaFailure();

        System.out.println("\n########## [OFFLINE] SCENARIO 7: Step cap guardrail (infinite loop) ##########");
        scenarioStepCapExceeded();

        System.out.println("\n########## [OFFLINE] SCENARIO 8: Template resolution against mock world ##########");
        scenarioTemplateResolution();

        System.out.println("\n########## [OFFLINE] SCENARIO 9: Idempotency dedup - direct mock-world proof ##########");
        scenarioIdempotencyDedupProof();

        System.out.println("\nAll OFFLINE scenarios finished.\n");
    }

    // ---------- Scenario 1 ----------
    private void scenarioSimpleNotify() throws Exception {
        String definition = """
            {"entryNodeId":"n1","nodes":[
              {"id":"n1","type":"NOTIFY","config":{"message":"Order received","channel":"ops"}}
            ],"edges":[]}
            """;
        Workflow wf = workflowService.createDraft("offline-demo-notify", definition);
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
        Workflow wf = workflowService.createDraft("offline-demo-condition", definition);
        workflowService.publish(wf.getId());

        Run runHigh = trigger(wf.getId(), "{\"priority\":\"high\"}");
        Run finishedHigh = pollUntilTerminal(runHigh.getId());
        assertExpected("urgent", lastCompletedNodeId(finishedHigh.getId()));

        Run runLow = trigger(wf.getId(), "{\"priority\":\"low\"}");
        Run finishedLow = pollUntilTerminal(runLow.getId());
        assertExpected("normal", lastCompletedNodeId(finishedLow.getId()));
    }

    // ---------- Scenario 3 ----------
    private void scenarioApprovalGateApprove() throws Exception {
        String definition = """
            {"entryNodeId":"place-order","nodes":[
              {"id":"place-order","type":"HTTP_REQUEST","config":{
                 "url":"%s/orders","method":"POST","requiresApproval":true}}
            ],"edges":[]}
            """.formatted(mockWorldBaseUrl);
        Workflow wf = workflowService.createDraft("offline-demo-approve", definition);
        workflowService.publish(wf.getId());

        Run run = trigger(wf.getId(), "{\"orderId\":\"ORD-APPROVE\"}");
        Run paused = pollUntilTerminal(run.getId());
        assertExpected("WAITING_APPROVAL", paused.getStatus().name());

        Approval approval = approvalRepository.findByStepId(latestStepId(run.getId()))
                .orElseThrow(() -> new IllegalStateException("Expected an approval record"));
        System.out.println("Approving approvalId=" + approval.getId() + " ...");
        approvalService.approve(approval.getId(), "demo-manager");

        Run resumed = pollUntilTerminal(run.getId());
        printTrace(resumed.getId());
        assertExpected("COMPLETED", resumed.getStatus().name());
    }

    // ---------- Scenario 4 ----------
    private void scenarioApprovalGateReject() throws Exception {
        String definition = """
            {"entryNodeId":"place-order","nodes":[
              {"id":"place-order","type":"HTTP_REQUEST","config":{
                 "url":"%s/orders","method":"POST","requiresApproval":true}}
            ],"edges":[]}
            """.formatted(mockWorldBaseUrl);
        Workflow wf = workflowService.createDraft("offline-demo-reject", definition);
        workflowService.publish(wf.getId());

        Run run = trigger(wf.getId(), "{\"orderId\":\"ORD-REJECT\"}");
        Run paused = pollUntilTerminal(run.getId());
        assertExpected("WAITING_APPROVAL", paused.getStatus().name());

        Approval approval = approvalRepository.findByStepId(latestStepId(run.getId()))
                .orElseThrow(() -> new IllegalStateException("Expected an approval record"));
        System.out.println("Rejecting approvalId=" + approval.getId() + " ...");
        approvalService.reject(approval.getId(), "demo-manager");

        Run rejected = pollUntilTerminal(run.getId());
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
        Workflow wf = workflowService.createDraft("offline-demo-ai-ok", definition);
        workflowService.publish(wf.getId());
        Run run = trigger(wf.getId(), "{\"orderId\":\"ORD-AI-1\"}");
        Run finished = pollUntilTerminal(run.getId());
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
        Workflow wf = workflowService.createDraft("offline-demo-ai-bad", definition);
        workflowService.publish(wf.getId());
        Run run = trigger(wf.getId(), "{\"orderId\":\"ORD-AI-2\"}");
        Run finished = pollUntilTerminal(run.getId());
        assertExpected("FAILED", finished.getStatus().name());
    }

    // ---------- Scenario 7 ----------
    private void scenarioStepCapExceeded() throws Exception {
        String definition = """
            {"entryNodeId":"n1","nodes":[
              {"id":"n1","type":"NOTIFY","config":{"message":"loop-a","channel":"ops"}},
              {"id":"n2","type":"NOTIFY","config":{"message":"loop-b","channel":"ops"}}
            ],"edges":[
              {"fromNodeId":"n1","toNodeId":"n2"},
              {"fromNodeId":"n2","toNodeId":"n1"}
            ]}
            """;
        Workflow wf = workflowService.createDraft("offline-demo-loop", definition);
        workflowService.publish(wf.getId());
        Run run = trigger(wf.getId(), "{}");
        Run finished = pollUntilTerminal(run.getId(), 15000);
        assertExpected("FAILED", finished.getStatus().name());
        System.out.println("stepsExecuted at halt: " + finished.getStepsExecuted() +
                " (maxSteps=" + finished.getMaxSteps() + ")");
    }

    // ---------- Scenario 8 ----------
    private void scenarioTemplateResolution() throws Exception {
        String definition = """
            {"entryNodeId":"n1","nodes":[
              {"id":"n1","type":"HTTP_REQUEST","config":{
                 "url":"%s/orders/{{trigger.orderId}}","method":"GET"}}
            ],"edges":[]}
            """.formatted(mockWorldBaseUrl);
        Workflow wf = workflowService.createDraft("offline-demo-template", definition);
        workflowService.publish(wf.getId());
        Run run = trigger(wf.getId(), "{\"orderId\":\"ORD-TEMPLATE-42\"}");
        pollUntilTerminal(run.getId());
        Step step = stepRepository.findByRunIdOrderBySequenceIndexAsc(run.getId()).get(0);
        System.out.println("resolvedInput (should show real orderId, not {{...}}): " + step.getResolvedInput());
        assertExpected(true, step.getResolvedInput().contains("ORD-TEMPLATE-42"));
        assertExpected(false, step.getResolvedInput().contains("{{"));
    }

    // ---------- Scenario 9: direct proof, bypassing the engine entirely ----------
    private void scenarioIdempotencyDedupProof() throws Exception {
        resetMockWorld();
        String idempotencyKey = UUID.randomUUID().toString();
        String body = "{\"orderId\":\"ORD-DEDUP-TEST\"}";

        System.out.println("Calling mock world TWICE with the SAME idempotency key: " + idempotencyKey);

        JsonNode first = postToMockWorld(idempotencyKey, body);
        JsonNode second = postToMockWorld(idempotencyKey, body);

        System.out.println("First call -> orderId=" + first.get("orderId").asText() +
                " duplicate=" + first.get("duplicate").asBoolean());
        System.out.println("Second call -> orderId=" + second.get("orderId").asText() +
                " duplicate=" + second.get("duplicate").asBoolean());

        assertExpected(false, first.get("duplicate").asBoolean());
        assertExpected(true, second.get("duplicate").asBoolean());
        assertExpected(first.get("orderId").asText(), second.get("orderId").asText());
        System.out.println("Confirmed: mock world recognized the retry and did NOT create a second order.");
    }

    private JsonNode postToMockWorld(String idempotencyKey, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mockWorldBaseUrl + "/orders"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readTree(response.body());
    }

    private void resetMockWorld() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mockWorldBaseUrl + "/reset"))
                .timeout(Duration.ofSeconds(5))
                .DELETE()
                .build();
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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
                "(current status=" + run.getStatus() + ").");
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
        System.out.printf(" -> runId=%d status=%s stepsExecuted=%d/%d%n",
                run.getId(), run.getStatus(), run.getStepsExecuted(), run.getMaxSteps());
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
        return s.length() > 150 ? s.substring(0, 150) + "..." : s;
    }

    private void assertExpected(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            System.out.println(" *** MISMATCH *** expected=" + expected + " actual=" + actual);
        } else {
            System.out.println(" OK - expected=" + expected);
        }
    }
}
