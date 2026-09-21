package com.relay.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "relay.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockAiProvider implements AiProvider {

    @Override
    public AiCompletionResponse complete(AiCompletionRequest request) {
        String content = request.getMockResponseOverride() != null
                ? request.getMockResponseOverride()
                : "{\"result\":\"mock-response\"}";

        int promptTokens = request.getPrompt().split("\\s+").length;
        int completionTokens = content.split("\\s+").length;

        return new AiCompletionResponse(content, promptTokens, completionTokens);
    }
}
