package com.relay.engine;

/**
 * What a NodeExecutor hands back to the engine. `branch` is only
 * meaningful for CONDITION nodes - it tells the engine which outgoing
 * edge to follow. promptTokens/completionTokens are unused by
 * deterministic executors today; reserved for the AI executor.
 */
public class NodeExecutionResult {

    private final String outputJson;
    private final String branch;
    private final Integer promptTokens;
    private final Integer completionTokens;

    public NodeExecutionResult(String outputJson) {
        this(outputJson, null, null, null);
    }

    public NodeExecutionResult(String outputJson, String branch) {
        this(outputJson, branch, null, null);
    }

    public NodeExecutionResult(String outputJson, String branch, Integer promptTokens, Integer completionTokens) {
        this.outputJson = outputJson;
        this.branch = branch;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
    }

    public String getOutputJson() { return outputJson; }
    public String getBranch() { return branch; }
    public Integer getPromptTokens() { return promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
}
