package com.relay.trigger.dto;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.NotNull;

/**
 * Body for the manual trigger API. The payload becomes the run's input,
 * exactly as it would for a webhook-triggered run - this exists purely
 * for testing and demos without needing to hit the webhook endpoint.
 */
public class TriggerRequest {

    @NotNull
    private ObjectNode payload;

    public TriggerRequest() {
        // Jackson
    }

    public TriggerRequest(ObjectNode payload) {
        this.payload = payload;
    }

    public ObjectNode getPayload() { return payload; }
    public void setPayload(ObjectNode payload) { this.payload = payload; }
}