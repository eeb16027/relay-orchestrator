package com.relay.workflow.dto;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.NotNull;

public class UpdateDraftRequest {

    @NotNull
    private ObjectNode definition;

    public UpdateDraftRequest() {
        // Jackson
    }

    public UpdateDraftRequest(ObjectNode definition) {
        this.definition = definition;
    }

    public ObjectNode getDefinition() { return definition; }
    public void setDefinition(ObjectNode definition) { this.definition = definition; }
}
