package com.relay.workflow.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "runs")
public class Run {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id", nullable = false)
    private Long workflowId;

    @Column(name = "workflow_version_id", nullable = false)
    private Long workflowVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunStatus status = RunStatus.RUNNING;

    @Column(name = "trigger_payload", columnDefinition = "JSON")
    private String triggerPayload;

    // Index of the next node to execute, in topologically resolved order
    @Column(name = "current_step_index", nullable = false)
    private int currentStepIndex = 0;

    // Guardrail: hard cap on total steps executed for this run
    @Column(name = "steps_executed", nullable = false)
    private int stepsExecuted = 0;

    @Column(name = "max_steps", nullable = false)
    private int maxSteps = 100;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Run() {
        // JPA
    }

    public Run(Long workflowId, Long workflowVersionId, String triggerPayload, int maxSteps) {
        this.workflowId = workflowId;
        this.workflowVersionId = workflowVersionId;
        this.triggerPayload = triggerPayload;
        this.maxSteps = maxSteps;
    }

    public boolean hasExceededStepCap() {
        return stepsExecuted >= maxSteps;
    }

    public void incrementStepsExecuted() {
        this.stepsExecuted++;
        this.updatedAt = Instant.now();
    }

    // --- getters / setters ---

    public Long getId() { return id; }
    public Long getWorkflowId() { return workflowId; }
    public Long getWorkflowVersionId() { return workflowVersionId; }

    public RunStatus getStatus() { return status; }
    public void setStatus(RunStatus status) { this.status = status; this.updatedAt = Instant.now(); }

    public String getTriggerPayload() { return triggerPayload; }

    public int getCurrentStepIndex() { return currentStepIndex; }
    public void setCurrentStepIndex(int currentStepIndex) { this.currentStepIndex = currentStepIndex; }

    public int getStepsExecuted() { return stepsExecuted; }
    public int getMaxSteps() { return maxSteps; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
