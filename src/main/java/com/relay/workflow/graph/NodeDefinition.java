package com.relay.workflow.graph;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.workflow.model.NodeType;

public class NodeDefinition {
	
	private String Id;
	private NodeType type;
	private String label;
	
	private ObjectNode config;

	public NodeDefinition() {
		//jackson
	}

	public NodeDefinition(String id, NodeType type, String label, ObjectNode config) {
		super();
		Id = id;
		this.type = type;
		this.label = label;
		this.config = config;
	}

	public String getId() {
		return Id;
	}

	public void setId(String id) {
		Id = id;
	}

	public NodeType getType() {
		return type;
	}

	public void setType(NodeType type) {
		this.type = type;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public ObjectNode getConfig() {
		return config;
	}

	public void setConfig(ObjectNode config) {
		this.config = config;
	}
	
	
	
	

}
