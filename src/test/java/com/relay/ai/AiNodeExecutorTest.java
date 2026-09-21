package com.relay.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.engine.NodeExecutionContext;
import com.relay.engine.NodeExecutionException;
import com.relay.engine.NodeExecutionResult;
import com.relay.engine.TemplateResolver;
import com.relay.workflow.model.NodeType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiNodeExecutorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TemplateResolver templateResolver = new TemplateResolver(objectMapper);
    private final JsonSchemaValidator schemaValidator = new JsonSchemaValidator(objectMapper);
    private final PromptInjectionGuard promptInjectionGuard = new PromptInjectionGuard();

    private NodeExecutionContext contextWith(String configJson) throws Exception {
        ObjectNode config = (ObjectNode) objectMapper.readTree(configJson);
        String runContext = "{\"trigger\":{\"orderId\":\"ORD-1\"},\"steps\":{}}";
        return new NodeExecutionContext(1L, 1L, "ai1", NodeType.AI, runContext, config, "idem-1");
    }

    @Test
    void execute_returnsMockResponseWhenSchemaMatches() throws Exception {
        AiNodeExecutor executor = new AiNodeExecutor(new MockAiProvider(), templateResolver,
                schemaValidator, objectMapper, promptInjectionGuard);

        String configJson = """
            {"promptTemplate":"Classify order {{trigger.orderId}}",
             "outputSchema":{"type":"object","properties":{"category":{"type":"string"}},"required":["category"]},
             "mockResponse":{"category":"urgent"}}
            """;

        NodeExecutionResult result = executor.execute(contextWith(configJson));
        assertTrue(result.getOutputJson().contains("urgent"));
    }

    @Test
    void execute_throwsWhenOutputFailsSchemaValidation() throws Exception {
        AiNodeExecutor executor = new AiNodeExecutor(new MockAiProvider(), templateResolver,
                schemaValidator, objectMapper, promptInjectionGuard);

        String configJson = """
            {"promptTemplate":"Classify order {{trigger.orderId}}",
             "outputSchema":{"type":"object","properties":{"category":{"type":"string"}},"required":["category"]},
             "mockResponse":{"wrongField":"oops"}}
            """;

        assertThrows(NodeExecutionException.class, () -> executor.execute(contextWith(configJson)));
    }
}