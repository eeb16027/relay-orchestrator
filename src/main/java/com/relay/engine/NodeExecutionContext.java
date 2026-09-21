package com.relay.engine;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.workflow.model.NodeType;

/**
 * Everything a NodeExecutor needs to run a single step. Deliberately
 * carries only primitives/JSON, not entities - executors should never
 * touch the database directly; persistence is the engine's job.
 */
public class NodeExecutionContext {

    private final Long runId;
    private final Long stepId;
    private final String nodeId;
    private final NodeType nodeType;
    private final String resolvedInputJson;
    private final ObjectNode config;
    private final String idempotencyKey;

    public NodeExecutionContext(Long runId, Long stepId, String nodeId, NodeType nodeType,
                                 String resolvedInputJson, ObjectNode config, String idempotencyKey) {
        this.runId = runId;
        this.stepId = stepId;
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.resolvedInputJson = resolvedInputJson;
        this.config = config;
        this.idempotencyKey=idempotencyKey;
    }

    public Long getRunId() { return runId; }
    public Long getStepId() { return stepId; }
    public String getNodeId() { return nodeId; }
    public NodeType getNodeType() { return nodeType; }
    public String getResolvedInputJson() { return resolvedInputJson; }
    public ObjectNode getConfig() { return config; }

	public String getIdempotencyKey() {
		return idempotencyKey;
	}
    
}
