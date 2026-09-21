package com.relay.ai;

public class AiCompletionResponse {
	
	private final String content;
	private final Integer promptTokens;
	private final Integer completionTokens;
	
	public AiCompletionResponse(String content, Integer promptTokens, Integer completionTokens) {
		super();
		this.content = content;
		this.promptTokens = promptTokens;
		this.completionTokens = completionTokens;
	}

	public String getContent() {
		return content;
	}

	public Integer getPromptTokens() {
		return promptTokens;
	}

	public Integer getCompletionTokens() {
		return completionTokens;
	}
	
	
	
	
	

}
