package com.relay.workflow.service;

import com.relay.workflow.model.*;
import com.relay.workflow.repository.ApprovalRepository;
import com.relay.workflow.repository.RunRepository;
import com.relay.workflow.repository.StepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ApprovalService {

    private final ApprovalRepository approvalRepository;
    private final StepRepository stepRepository;
    private final RunRepository runRepository;

    public ApprovalService(ApprovalRepository approvalRepository,
                            StepRepository stepRepository,
                            RunRepository runRepository) {
        this.approvalRepository = approvalRepository;
        this.stepRepository = stepRepository;
        this.runRepository = runRepository;
    }

    /**
     * Approves the gate. Re-queues the step as PENDING so the worker
     * picks it up again - at that point, an APPROVED approval record
     * will exist, so the engine lets the real executor (or, for a bare
     * APPROVAL node, an immediate success) proceed.
     */
    @Transactional
    public Approval approve(Long approvalId, String approver) {
        Approval approval = getPendingApprovalOrThrow(approvalId);
        approval.approve(approver);
        approvalRepository.save(approval);

        Step step = stepRepository.findById(approval.getStepId())
                .orElseThrow(() -> new IllegalStateException("Step not found: " + approval.getStepId()));
        step.setStatus(StepStatus.PENDING);
        stepRepository.save(step);

        Run run = runRepository.findById(approval.getRunId())
                .orElseThrow(() -> new IllegalStateException("Run not found: " + approval.getRunId()));
        run.setStatus(RunStatus.RUNNING);
        runRepository.save(run);

        return approval;
    }

    /**
     * Rejects the gate. The step and its run fail immediately - a
     * rejected sensitive action must never be retried automatically.
     */
    @Transactional
    public Approval reject(Long approvalId, String approver) {
        Approval approval = getPendingApprovalOrThrow(approvalId);
        approval.reject(approver);
        approvalRepository.save(approval);

        Step step = stepRepository.findById(approval.getStepId())
                .orElseThrow(() -> new IllegalStateException("Step not found: " + approval.getStepId()));
        step.markFailed("Rejected by approver" + (approver != null ? " (" + approver + ")" : ""));
        stepRepository.save(step);

        Run run = runRepository.findById(approval.getRunId())
                .orElseThrow(() -> new IllegalStateException("Run not found: " + approval.getRunId()));
        run.setStatus(RunStatus.FAILED);
        run.setFailureReason("Approval " + approvalId + " rejected at node " + step.getNodeId());
        runRepository.save(run);

        return approval;
    }

    @Transactional(readOnly = true)
    public Approval getOrThrow(Long approvalId) {
        return approvalRepository.findById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("Approval not found: " + approvalId));
    }

    @Transactional(readOnly = true)
    public List<Approval> listAll() {
        return approvalRepository.findAll();
    }

    private Approval getPendingApprovalOrThrow(Long approvalId) {
        Approval approval = getOrThrow(approvalId);
        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException(
                    "Approval " + approvalId + " has already been decided (" + approval.getStatus() + ").");
        }
        return approval;
    }
}