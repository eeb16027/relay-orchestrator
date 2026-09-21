package com.relay.trigger.dto;

import com.relay.workflow.model.RunStatus;

import java.time.Instant;

/**
 * What callers get back after triggering a run - just enough to poll
 * status or look up the full trace later. Deliberately thin; the CLI's
 * `trace <runId>` command is the place for full step-by-step detail.
 */
public class RunResponse {

    private Long runId;
    private Long workflowId;
    private Long workflowVersionId;
    private RunStatus status;
    private Instant createdAt;

    public RunResponse() {
    }

    public RunResponse(Long runId, Long workflowId, Long workflowVersionId,
                        RunStatus status, Instant createdAt) {
        this.runId = runId;
        this.workflowId = workflowId;
        this.workflowVersionId = workflowVersionId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getRunId() { return runId; }
    public Long getWorkflowId() { return workflowId; }
    public Long getWorkflowVersionId() { return workflowVersionId; }
    public RunStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}