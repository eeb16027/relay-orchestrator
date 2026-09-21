package com.relay.trigger;

import com.relay.workflow.dto.*;
import com.relay.workflow.model.Workflow;
import com.relay.workflow.model.WorkflowVersion;
import com.relay.workflow.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    /**
     * Creates a new workflow in DRAFT status. `definition` is parsed for
     * valid JSON shape but not fully structurally validated yet - that
     * happens at publish time.
     */
    @PostMapping
    public ResponseEntity<WorkflowResponse> create(@Valid @RequestBody CreateWorkflowRequest request) {
        Workflow workflow = workflowService.createDraft(
                request.getName(),
                request.getDefinition().toString()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(WorkflowResponse.from(workflow));
    }

    @GetMapping
    public ResponseEntity<List<WorkflowResponse>> list() {
        List<WorkflowResponse> responses = workflowService.listWorkflows().stream()
                .map(WorkflowResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{workflowId}")
    public ResponseEntity<WorkflowResponse> get(@PathVariable Long workflowId) {
        Workflow workflow = workflowService.getWorkflowOrThrow(workflowId);
        return ResponseEntity.ok(WorkflowResponse.from(workflow));
    }

    /**
     * Overwrites the draft definition. Does not touch anything already
     * published - only publish() freezes a new snapshot.
     */
    @PutMapping("/{workflowId}/draft")
    public ResponseEntity<WorkflowResponse> updateDraft(
            @PathVariable Long workflowId,
            @Valid @RequestBody UpdateDraftRequest request) {
        Workflow workflow = workflowService.updateDraftDefinition(
                workflowId,
                request.getDefinition().toString()
        );
        return ResponseEntity.ok(WorkflowResponse.from(workflow));
    }

    /**
     * Validates the current draft and, if valid, freezes it into a new
     * immutable WorkflowVersion. Flips the workflow to PUBLISHED.
     */
    @PostMapping("/{workflowId}/publish")
    public ResponseEntity<WorkflowVersionResponse> publish(@PathVariable Long workflowId) {
        WorkflowVersion version = workflowService.publish(workflowId);
        return ResponseEntity.ok(WorkflowVersionResponse.from(version));
    }

    @GetMapping("/{workflowId}/versions")
    public ResponseEntity<List<WorkflowVersionResponse>> listVersions(@PathVariable Long workflowId) {
        List<WorkflowVersionResponse> responses = workflowService.listVersions(workflowId).stream()
                .map(WorkflowVersionResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }
}
