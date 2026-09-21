package com.relay.workflow.dto;

import com.relay.workflow.model.Workflow;
import com.relay.workflow.model.WorkflowStatus;

import java.time.Instant;

public class WorkflowResponse {

    private Long id;
    private String name;
    private WorkflowStatus status;
    private Long publishedVersionId;
    private String webhookSecret;
    private Instant createdAt;
    private Instant updatedAt;

    public WorkflowResponse() {
    }

    public static WorkflowResponse from(Workflow workflow) {
        WorkflowResponse response = new WorkflowResponse();
        response.id = workflow.getId();
        response.name = workflow.getName();
        response.status = workflow.getStatus();
        response.publishedVersionId = workflow.getPublishedVersionId();
        response.webhookSecret = workflow.getWebhookSecret();
        response.createdAt = workflow.getCreatedAt();
        response.updatedAt = workflow.getUpdatedAt();
        return response;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public WorkflowStatus getStatus() { return status; }
    public Long getPublishedVersionId() { return publishedVersionId; }
    public String getWebhookSecret() { return webhookSecret; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
