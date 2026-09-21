package com.relay.engine.executor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.engine.NodeExecutionContext;
import com.relay.engine.NodeExecutionException;
import com.relay.engine.NodeExecutionResult;
import com.relay.engine.NodeExecutor;
import com.relay.workflow.model.NodeType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class HttpRequestNodeExecutor implements NodeExecutor {

    private final HttpClient httpClient;
    private final long timeoutMs;

    public HttpRequestNodeExecutor(@Value("${relay.engine.http.timeout-ms}") long timeoutMs) {
        this.timeoutMs = timeoutMs;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    @Override
    public NodeType getSupportedType() {
        return NodeType.HTTP_REQUEST;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        ObjectNode config = context.getConfig();
        String url = requireField(config, "url");
        String method = config.has("method") ? config.get("method").asText() : "GET";
        String body = config.has("body") ? config.get("body").asText() : "";

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs));
            if(context.getIdempotencyKey()!=null) {
            	builder.header("idempotency-key", context.getIdempotencyKey());
            }

            switch (method.toUpperCase()) {
                case "GET" -> builder.GET();
                case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body));
                case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(body));
                case "DELETE" -> builder.DELETE();
                default -> throw new NodeExecutionException("Unsupported HTTP method: " + method);
            }

            HttpRequest httpRequest = builder.build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            String outputJson = String.format(
                    "{\"statusCode\":%d,\"body\":%s}",
                    response.statusCode(),
                    toJsonStringLiteral(response.body())
            );
            return new NodeExecutionResult(outputJson);

        } catch (java.io.IOException | InterruptedException e) {
            throw new NodeExecutionException("HTTP request failed: " + e.getMessage(), e);
        }
    }

    private String requireField(ObjectNode config, String field) {
        if (!config.has(field)) {
            throw new NodeExecutionException("HTTP_REQUEST node config missing required field: " + field);
        }
        return config.get(field).asText();
    }

    // Minimal JSON-string escaping so raw response bodies can be embedded
    // safely as a JSON string value in outputJson.
    private String toJsonStringLiteral(String raw) {
        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}