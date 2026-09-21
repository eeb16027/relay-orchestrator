package com.relay.ai;

public interface AiProvider {
    AiCompletionResponse complete(AiCompletionRequest request);
}