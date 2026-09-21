package com.relay.trigger;

import com.relay.workflow.dto.ApprovalDecisionRequest;
import com.relay.workflow.dto.ApprovalResponse;
import com.relay.workflow.model.Approval;
import com.relay.workflow.service.ApprovalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping
    public ResponseEntity<List<ApprovalResponse>> list() {
        List<ApprovalResponse> responses = approvalService.listAll().stream()
                .map(ApprovalResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{approvalId}")
    public ResponseEntity<ApprovalResponse> get(@PathVariable Long approvalId) {
        Approval approval = approvalService.getOrThrow(approvalId);
        return ResponseEntity.ok(ApprovalResponse.from(approval));
    }

    @PostMapping("/{approvalId}/approve")
    public ResponseEntity<ApprovalResponse> approve(
            @PathVariable Long approvalId,
            @RequestBody(required = false) ApprovalDecisionRequest request) {
        String approver = request != null ? request.getApprover() : null;
        Approval approval = approvalService.approve(approvalId, approver);
        return ResponseEntity.ok(ApprovalResponse.from(approval));
    }

    @PostMapping("/{approvalId}/reject")
    public ResponseEntity<ApprovalResponse> reject(
            @PathVariable Long approvalId,
            @RequestBody(required = false) ApprovalDecisionRequest request) {
        String approver = request != null ? request.getApprover() : null;
        Approval approval = approvalService.reject(approvalId, approver);
        return ResponseEntity.ok(ApprovalResponse.from(approval));
    }
}
