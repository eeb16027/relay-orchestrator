package com.relay.workflow.dto;

import com.relay.workflow.model.Approval;
import com.relay.workflow.model.ApprovalStatus;

import java.time.Instant;

public class ApprovalResponse {

    private Long id;
    private Long runId;
    private Long stepId;
    private ApprovalStatus status;
    private String approver;
    private Instant createdAt;
    private Instant decidedAt;

    public static ApprovalResponse from(Approval approval) {
        ApprovalResponse response = new ApprovalResponse();
        response.id = approval.getId();
        response.runId = approval.getRunId();
        response.stepId = approval.getStepId();
        response.status = approval.getStatus();
        response.approver = approval.getApprover();
        response.createdAt = approval.getCreatedAt();
        response.decidedAt = approval.getDecidedAt();
        return response;
    }

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public Long getStepId() { return stepId; }
    public ApprovalStatus getStatus() { return status; }
    public String getApprover() { return approver; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
}