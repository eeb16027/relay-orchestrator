package com.relay.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TemplateResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TemplateResolver resolver = new TemplateResolver(objectMapper);

    private JsonNode runData() throws Exception {
        return objectMapper.readTree("""
            {"trigger":{"orderId":"ORD-1"},"steps":{"n1":{"output":{"total":42}}}}
            """);
    }

    @Test
    void resolve_substitutesExactMatchPlaceholder() throws Exception {
        ObjectNode config = (ObjectNode) objectMapper.readTree("{\"url\":\"{{trigger.orderId}}\"}");
        ObjectNode resolved = resolver.resolve(config, runData());
        assertEquals("ORD-1", resolved.get("url").asText());
    }

    @Test
    void resolve_leavesNonPlaceholderTextUnchanged() throws Exception {
        ObjectNode config = (ObjectNode) objectMapper.readTree("{\"method\":\"POST\"}");
        ObjectNode resolved = resolver.resolve(config, runData());
        assertEquals("POST", resolved.get("method").asText());
    }

    @Test
    void resolve_returnsNullForMissingPath() throws Exception {
        ObjectNode config = (ObjectNode) objectMapper.readTree("{\"missing\":\"{{trigger.doesNotExist}}\"}");
        ObjectNode resolved = resolver.resolve(config, runData());
        assertTrue(resolved.get("missing").isNull());
    }

    @Test
    void interpolateText_substitutesEmbeddedPlaceholders() throws Exception {
        String template = "Order {{trigger.orderId}} totals {{steps.n1.output.total}} dollars.";
        assertEquals("Order ORD-1 totals 42 dollars.", resolver.interpolateText(template, runData()));
    }

    @Test
    void interpolateText_leavesUnresolvedPlaceholderAsEmpty() throws Exception {
        assertEquals("Value: ", resolver.interpolateText("Value: {{trigger.missing}}", runData()));
    }
}
