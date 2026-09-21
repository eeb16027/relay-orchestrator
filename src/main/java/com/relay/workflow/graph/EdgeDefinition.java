package com.relay.workflow.graph;

public class EdgeDefinition {
	
	private String fromNodeId;
	private String toNodeId;
	private String branch;
	
	public EdgeDefinition() {
		
	}

	public EdgeDefinition(String fromNodeId, String toNodeId, String branch) {
		super();
		this.fromNodeId = fromNodeId;
		this.toNodeId = toNodeId;
		this.branch = branch;
	}

	public String getFromNodeId() {
		return fromNodeId;
	}

	public void setFromNodeId(String fromNodeId) {
		this.fromNodeId = fromNodeId;
	}

	public String getToNodeId() {
		return toNodeId;
	}

	public void setToNodeId(String toNodeId) {
		this.toNodeId = toNodeId;
	}

	public String getBranch() {
		return branch;
	}

	public void setBranch(String branch) {
		this.branch = branch;
	}
	
	
	
	

}
