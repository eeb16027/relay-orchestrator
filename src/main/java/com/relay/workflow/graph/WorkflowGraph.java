package com.relay.workflow.graph;

import java.util.List;

public class WorkflowGraph {
	
	private List<NodeDefinition> nodes;
	private List<EdgeDefinition> edges;
	
	private String entryNodeId;

	public WorkflowGraph() {
		
	}

	public WorkflowGraph(List<NodeDefinition> nodes, List<EdgeDefinition> edges, String entryNodeId) {
		super();
		this.nodes = nodes;
		this.edges = edges;
		this.entryNodeId = entryNodeId;
	}

	public List<NodeDefinition> getNodes() {
		return nodes;
	}

	public void setNodes(List<NodeDefinition> nodes) {
		this.nodes = nodes;
	}

	public List<EdgeDefinition> getEdges() {
		return edges;
	}

	public void setEdges(List<EdgeDefinition> edges) {
		this.edges = edges;
	}

	public String getEntryNodeId() {
		return entryNodeId;
	}

	public void setEntryNodeId(String entryNodeId) {
		this.entryNodeId = entryNodeId;
	}
	
	

}
