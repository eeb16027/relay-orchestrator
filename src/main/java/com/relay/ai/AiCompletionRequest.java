package com.relay.ai;

public class AiCompletionRequest {

    private final String prompt;
    private final String mockResponseOverride;

    public AiCompletionRequest(String prompt, String mockResponseOverride) {
        this.prompt = prompt;
        this.mockResponseOverride = mockResponseOverride;
    }

    public String getPrompt() { return prompt; }
    public String getMockResponseOverride() { return mockResponseOverride; }
}