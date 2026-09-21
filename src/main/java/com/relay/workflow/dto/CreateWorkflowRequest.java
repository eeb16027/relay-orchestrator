package com.relay.workflow.dto;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateWorkflowRequest {

    @NotBlank
    private String name;

    @NotNull
    private ObjectNode definition;

    public CreateWorkflowRequest() {
        // Jackson
    }

    public CreateWorkflowRequest(String name, ObjectNode definition) {
        this.name = name;
        this.definition = definition;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ObjectNode getDefinition() { return definition; }
    public void setDefinition(ObjectNode definition) { this.definition = definition; }
}
