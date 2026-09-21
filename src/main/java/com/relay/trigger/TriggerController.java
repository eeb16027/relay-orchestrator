package com.relay.trigger;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.trigger.dto.RunResponse;
import com.relay.trigger.dto.TriggerRequest;
import com.relay.workflow.exception.InvalidWebhookSecretException;
import com.relay.workflow.model.Run;
import com.relay.workflow.model.Workflow;
import com.relay.workflow.service.RunService;
import com.relay.workflow.service.WorkflowService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workflows/{workflowId}")
public class TriggerController {

    private final WorkflowService workflowService;
    private final RunService runService;
    private final String webhookHeaderName;

    public TriggerController(WorkflowService workflowService,
                              RunService runService,
                              @Value("${relay.trigger.webhook.header-name}") String webhookHeaderName) {
        this.workflowService = workflowService;
        this.runService = runService;
        this.webhookHeaderName = webhookHeaderName;
    }

    /**
     * Webhook trigger. The caller must send the workflow's secret in the
     * configured header (default X-Relay-Signature). The raw JSON body
     * becomes the run's input payload.
     */
    @PostMapping("/webhook")
    public ResponseEntity<RunResponse> triggerViaWebhook(
            @PathVariable Long workflowId,
            @RequestBody ObjectNode payload,
            HttpServletRequest request) {

        Workflow workflow = workflowService.getWorkflowOrThrow(workflowId);
        String providedSecret = request.getHeader(webhookHeaderName);

        if (providedSecret == null || !providedSecret.equals(workflow.getWebhookSecret())) {
            throw new InvalidWebhookSecretException();
        }

        Run run = runService.startRun(workflowId, payload);
        return ResponseEntity.ok(toResponse(run));
    }

    /**
     * Manual trigger for testing/demos - no secret required, since this
     * is meant to be called directly by a developer or the CLI console.
     */
    @PostMapping("/trigger")
    public ResponseEntity<RunResponse> triggerManually(
            @PathVariable Long workflowId,
            @Valid @RequestBody TriggerRequest request) {

        Run run = runService.startRun(workflowId, request.getPayload());
        return ResponseEntity.ok(toResponse(run));
    }

    private RunResponse toResponse(Run run) {
        return new RunResponse(
                run.getId(),
                run.getWorkflowId(),
                run.getWorkflowVersionId(),
                run.getStatus(),
                run.getCreatedAt()
        );
    }
}