package com.relay.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.engine.NodeExecutionContext;
import com.relay.engine.NodeExecutionException;
import com.relay.engine.NodeExecutionResult;
import com.relay.engine.NodeExecutor;
import com.relay.engine.TemplateResolver;
import com.relay.workflow.model.NodeType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Config shape:
 * {
 * "promptTemplate": "Classify this order: {{trigger.orderId}}",
 * "outputSchema": { JSON Schema draft 2020-12 object },
 * "mockResponse": { ... } // only consulted by MockAiProvider
 * }
 *
 * The model's raw output is validated against outputSchema BEFORE the
 * engine advances to any downstream node - a malformed/hallucinated
 * shape throws NodeExecutionException, which the engine turns into a
 * FAILED step rather than silently passing bad data forward.
 */
@Component
public class AiNodeExecutor implements NodeExecutor {

    private final AiProvider aiProvider;
    private final TemplateResolver templateResolver;
    private final JsonSchemaValidator schemaValidator;
    private final ObjectMapper objectMapper;
    private final PromptInjectionGuard promptInjectionGuard;

    public AiNodeExecutor(AiProvider aiProvider,
                           TemplateResolver templateResolver,
                           JsonSchemaValidator schemaValidator,
                           ObjectMapper objectMapper,
                           PromptInjectionGuard promptInjectionGuard) {
        this.aiProvider = aiProvider;
        this.templateResolver = templateResolver;
        this.schemaValidator = schemaValidator;
        this.objectMapper = objectMapper;
        this.promptInjectionGuard = promptInjectionGuard;
    }

    @Override
    public NodeType getSupportedType() {
        return NodeType.AI;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        ObjectNode config = context.getConfig();
        if (!config.has("promptTemplate")) {
            throw new NodeExecutionException("AI node config missing required field: promptTemplate");
        }

        JsonNode runData;
        try {
            runData = objectMapper.readTree(context.getResolvedInputJson());
        } catch (Exception e) {
            throw new NodeExecutionException("Could not parse run context for AI node", e);
        }

        String rawPrompt = templateResolver.interpolateText(config.get("promptTemplate").asText(), runData);
        String prompt = promptInjectionGuard.sanitize(rawPrompt);

        String mockOverride = config.has("mockResponse") ? config.get("mockResponse").toString() : null;

        AiCompletionResponse response = aiProvider.complete(new AiCompletionRequest(prompt, mockOverride));

        if (config.has("outputSchema")) {
            String schemaJson = config.get("outputSchema").toString();
            List<String> errors = schemaValidator.validate(schemaJson, response.getContent());
            if (!errors.isEmpty()) {
                throw new NodeExecutionException(
                        "AI output failed schema validation: " + String.join("; ", errors));
            }
        }

        return new NodeExecutionResult(response.getContent(), null,
                response.getPromptTokens(), response.getCompletionTokens());
    }
}