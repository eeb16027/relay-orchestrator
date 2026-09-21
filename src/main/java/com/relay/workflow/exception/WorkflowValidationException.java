package com.relay.workflow.exception;

import java.util.List;



/**
 * Thrown when a workflow's draft definition fails structural validation
 * at publish time (e.g. dangling edges, missing entry node, unreachable
 * nodes). Carries every violation found, not just the first, so the
 * caller can report a complete list back to the user.
 */
public class WorkflowValidationException extends RuntimeException {
	
	 private final List<String> errors;

	    public WorkflowValidationException(List<String> errors) {
	        super("Workflow definition failed validation: " + String.join("; ", errors));
	        this.errors = errors;
	    }

	    public List<String> getErrors() {
	        return errors;
	    }

}
