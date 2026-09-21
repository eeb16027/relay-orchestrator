package com.relay.engine.executor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.engine.NodeExecutionContext;
import com.relay.engine.NodeExecutionException;
import com.relay.engine.NodeExecutionResult;
import com.relay.engine.NodeExecutor;
import com.relay.workflow.model.NodeType;
import org.springframework.stereotype.Component;

/**
 * NOTE: this is a simplification suitable for demo scale. It blocks the
 * calling worker thread for the configured duration rather than
 * rescheduling the step for later - a production version would persist
 * a "runAfter" timestamp and let the poller skip the step until then,
 * freeing the thread in the meantime.
 */
@Component
public class DelayNodeExecutor implements NodeExecutor {

    @Override
    public NodeType getSupportedType() {
        return NodeType.DELAY;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        ObjectNode config = context.getConfig();
        long durationMs = config.has("durationMs") ? config.get("durationMs").asLong() : 1000L;

        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NodeExecutionException("Delay was interrupted", e);
        }

        return new NodeExecutionResult("{\"delayedMs\":" + durationMs + "}");
    }
}