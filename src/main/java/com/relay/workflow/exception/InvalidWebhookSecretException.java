package com.relay.workflow.exception;

public class InvalidWebhookSecretException extends RuntimeException {

    public InvalidWebhookSecretException() {
        super("Webhook signature is missing or does not match the workflow's secret.");
    }
}
