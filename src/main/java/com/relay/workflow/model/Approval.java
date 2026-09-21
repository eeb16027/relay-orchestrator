package com.relay.workflow.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "approvals")
public class Approval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "step_id", nullable = false)
    private Long stepId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    private String approver;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Approval() {
        // JPA
    }

    public Approval(Long runId, Long stepId) {
        this.runId = runId;
        this.stepId = stepId;
    }

    public void approve(String approver) {
        this.status = ApprovalStatus.APPROVED;
        this.approver = approver;
        this.decidedAt = Instant.now();
    }

    public void reject(String approver) {
        this.status = ApprovalStatus.REJECTED;
        this.approver = approver;
        this.decidedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public Long getStepId() { return stepId; }
    public ApprovalStatus getStatus() { return status; }
    public String getApprover() { return approver; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
