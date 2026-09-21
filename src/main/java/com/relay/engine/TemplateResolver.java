package com.relay.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Two resolution modes:
 * - resolve(): structured node config where a value is EXACTLY
 * "{{path}}" and should become whatever type that path holds
 * (string, number, object...) - e.g. HTTP_REQUEST's "url" field.
 * - interpolateText(): freeform text with placeholders embedded
 * mid-sentence - e.g. an AI node's promptTemplate. Every {{path}}
 * occurrence is replaced with its stringified value, in place.
 */
@Component
public class TemplateResolver {

    private static final Pattern EXACT_PLACEHOLDER = Pattern.compile("^\\{\\{\\s*([\\w.]+)\\s*\\}\\}$");
    private static final Pattern EMBEDDED_PLACEHOLDER = Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*\\}\\}");

    private final ObjectMapper objectMapper;

    public TemplateResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode resolve(ObjectNode config, JsonNode runData) {
        ObjectNode result = objectMapper.createObjectNode();
        config.fields().forEachRemaining(entry ->
                result.set(entry.getKey(), resolveValue(entry.getValue(), runData)));
        return result;
    }

    public String interpolateText(String template, JsonNode runData) {
        Matcher matcher = EMBEDDED_PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            sb.append(template, lastEnd, matcher.start());
            JsonNode resolved = navigate(runData, matcher.group(1));
            sb.append(resolved.isMissingNode() || resolved.isNull() ? "" : resolved.asText());
            lastEnd = matcher.end();
        }
        sb.append(template.substring(lastEnd));
        return sb.toString();
    }

    private JsonNode resolveValue(JsonNode value, JsonNode runData) {
        if (value.isTextual()) {
            Matcher matcher = EXACT_PLACEHOLDER.matcher(value.asText());
            if (matcher.matches()) {
                JsonNode resolved = navigate(runData, matcher.group(1));
                return resolved.isMissingNode() ? NullNode.getInstance() : resolved;
            }
            return value;
        }
        if (value.isObject()) {
            ObjectNode nested = objectMapper.createObjectNode();
            value.fields().forEachRemaining(e -> nested.set(e.getKey(), resolveValue(e.getValue(), runData)));
            return nested;
        }
        if (value.isArray()) {
            ArrayNode arr = objectMapper.createArrayNode();
            value.forEach(v -> arr.add(resolveValue(v, runData)));
            return arr;
        }
        return value;
    }

    public JsonNode navigate(JsonNode root, String dotPath) {
        JsonNode current = root;
        for (String part : dotPath.split("\\.")) {
            if (current == null || current.isMissingNode()) {
                return MissingNode.getInstance();
            }
            current = current.path(part);
        }
        return current;
    }
}