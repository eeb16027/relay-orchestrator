package com.relay.workflow.dto;

public class ApprovalDecisionRequest {

    private String approver;

    public ApprovalDecisionRequest() {
        // Jackson
    }

    public String getApprover() { return approver; }
    public void setApprover(String approver) { this.approver = approver; }
}