package com.relay.engine;

import com.relay.workflow.model.NodeType;

/**
 * Contract every node-type implementation must satisfy. APPROVAL and AI
 * are deliberately not expected to have a NodeExecutor bean today:
 * APPROVAL is handled specially by the engine (it pauses the run rather
 * than "executing" anything), and AI will be added as its own executor
 * in a later slice.
 */
public interface NodeExecutor {

    NodeType getSupportedType();

    NodeExecutionResult execute(NodeExecutionContext context) throws NodeExecutionException;
}