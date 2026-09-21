package com.relay.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.engine.NodeExecutionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Real adapter to the Anthropic Messages API. Kept behind AiProvider so
 * engine tests never touch the network - MockAiProvider is used instead
 * whenever relay.ai.provider=mock (the default).
 */
@Component
@ConditionalOnProperty(name = "relay.ai.provider", havingValue = "real")
public class RealAiProvider implements AiProvider {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final long timeoutMs;

    public RealAiProvider(ObjectMapper objectMapper,
                           @Value("${relay.ai.api-key}") String apiKey,
                           @Value("${relay.ai.model}") String model,
                           @Value("${relay.engine.ai.timeout-ms}") long timeoutMs) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutMs = timeoutMs;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    @Override
    public AiCompletionResponse complete(AiCompletionRequest request) {
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "max_tokens", 1000,
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", request.getPrompt()
                    ))
            ));

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.anthropic.com/v1/messages"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (httpResponse.statusCode() >= 300) {
                throw new NodeExecutionException(
                        "AI provider returned HTTP " + httpResponse.statusCode() + ": " + httpResponse.body());
            }

            JsonNode body = objectMapper.readTree(httpResponse.body());
            String text = body.path("content").path(0).path("text").asText("");
            int promptTokens = body.path("usage").path("input_tokens").asInt(0);
            int completionTokens = body.path("usage").path("output_tokens").asInt(0);

            return new AiCompletionResponse(text, promptTokens, completionTokens);

        } catch (java.io.IOException | InterruptedException e) {
            throw new NodeExecutionException("AI provider call failed: " + e.getMessage(), e);
        }
    }
}