package com.relay.workflow.exception;

public class WorkflowNotFoundException extends RuntimeException {
	
	 public WorkflowNotFoundException(Long workflowId) {
	        super("Workflow not found with id: " + workflowId);
	    }

}
