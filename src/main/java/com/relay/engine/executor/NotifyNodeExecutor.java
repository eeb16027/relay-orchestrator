package com.relay.engine.executor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.engine.NodeExecutionContext;
import com.relay.engine.NodeExecutionResult;
import com.relay.engine.NodeExecutor;
import com.relay.workflow.model.NodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Simulated notification - logs the message rather than calling a real
 * notification service. Swap this out for an actual integration
 * (email/Slack/webhook) without touching the engine at all.
 */
@Component
public class NotifyNodeExecutor implements NodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(NotifyNodeExecutor.class);

    @Override
    public NodeType getSupportedType() {
        return NodeType.NOTIFY;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        ObjectNode config = context.getConfig();
        String message = config.has("message") ? config.get("message").asText() : "(no message)";
        String channel = config.has("channel") ? config.get("channel").asText() : "default";

        log.info("[NOTIFY] run={} node={} channel={} message={}",
                context.getRunId(), context.getNodeId(), channel, message);

        String outputJson = String.format(
                "{\"notified\":true,\"channel\":\"%s\",\"message\":\"%s\"}",
                channel, message.replace("\"", "\\\""));
        return new NodeExecutionResult(outputJson);
    }
}