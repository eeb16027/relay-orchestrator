package com.relay.workflow.dto;

import com.relay.workflow.model.WorkflowVersion;

import java.time.Instant;

public class WorkflowVersionResponse {

    private Long id;
    private Long workflowId;
    private int versionNumber;
    private String definition;
    private Instant publishedAt;

    public WorkflowVersionResponse() {
    }

    public static WorkflowVersionResponse from(WorkflowVersion version) {
        WorkflowVersionResponse response = new WorkflowVersionResponse();
        response.id = version.getId();
        response.workflowId = version.getWorkflowId();
        response.versionNumber = version.getVersionNumber();
        response.definition = version.getDefinition();
        response.publishedAt = version.getPublishedAt();
        return response;
    }

    public Long getId() { return id; }
    public Long getWorkflowId() { return workflowId; }
    public int getVersionNumber() { return versionNumber; }
    public String getDefinition() { return definition; }
    public Instant getPublishedAt() { return publishedAt; }
}
