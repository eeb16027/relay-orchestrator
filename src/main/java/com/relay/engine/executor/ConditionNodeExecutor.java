package com.relay.engine.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.engine.NodeExecutionContext;
import com.relay.engine.NodeExecutionException;
import com.relay.engine.NodeExecutionResult;
import com.relay.engine.NodeExecutor;
import com.relay.workflow.model.NodeType;
import org.springframework.stereotype.Component;

@Component
public class ConditionNodeExecutor implements NodeExecutor {

    private final ObjectMapper objectMapper;

    public ConditionNodeExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public NodeType getSupportedType() {
        return NodeType.CONDITION;
    }

    /**
     * Config shape: { "field": "orderId", "operator": "equals", "value": "12345" }
     * "field" is a dot-path into the resolved input JSON (e.g. "order.total").
     * Supported operators: equals, notEquals, exists.
     */
    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        ObjectNode config = context.getConfig();
        if (!config.has("field") || !config.has("operator")) {
            throw new NodeExecutionException("CONDITION node config requires 'field' and 'operator'.");
        }

        String fieldPath = config.get("field").asText();
        String operator = config.get("operator").asText();
        String expectedValue = config.has("value") ? config.get("value").asText() : null;

        JsonNode input;
        try {
            input = objectMapper.readTree(context.getResolvedInputJson());
        } catch (Exception e) {
            throw new NodeExecutionException("Could not parse resolved input as JSON", e);
        }

        JsonNode actual = navigate(input, fieldPath);
        boolean result = switch (operator) {
            case "exists" -> !actual.isMissingNode();
            case "equals" -> !actual.isMissingNode() && actual.asText().equals(expectedValue);
            case "notEquals" -> actual.isMissingNode() || !actual.asText().equals(expectedValue);
            default -> throw new NodeExecutionException("Unsupported CONDITION operator: " + operator);
        };

        String branch = String.valueOf(result);
        String outputJson = "{\"result\":" + result + "}";
        return new NodeExecutionResult(outputJson, branch);
    }

    private JsonNode navigate(JsonNode root, String dotPath) {
        JsonNode current = root;
        for (String part : dotPath.split("\\.")) {
            if (current == null || current.isMissingNode()) {
                return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
            }
            current = current.path(part);
        }
        return current;
    }
}