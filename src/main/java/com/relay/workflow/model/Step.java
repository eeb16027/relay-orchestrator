package com.relay.workflow.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "steps")
public class Step {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "node_id", nullable = false)
    private String nodeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false)
    private NodeType nodeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StepStatus status = StepStatus.PENDING;

    // Order of execution within the run - used for polling and audit
    @Column(name = "sequence_index", nullable = false)
    private int sequenceIndex;

    @Column(name = "resolved_input", columnDefinition = "JSON")
    private String resolvedInput;

    @Column(name = "output", columnDefinition = "JSON")
    private String output;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message")
    private String errorMessage;

    protected Step() {
        // JPA
    }

    public Step(Long runId, String nodeId, NodeType nodeType, int sequenceIndex, String resolvedInput) {
        this.runId = runId;
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.sequenceIndex = sequenceIndex;
        this.resolvedInput = resolvedInput;
    }

    public void markRunning() {
        this.status = StepStatus.RUNNING;
        this.attemptCount++;
        this.startedAt = Instant.now();
    }

    public void markCompleted(String output) {
        this.status = StepStatus.COMPLETED;
        this.output = output;
        this.completedAt = Instant.now();
    }

    public void markFailed(String errorMessage) {
        this.status = StepStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = Instant.now();
    }

    // --- getters / setters ---

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public String getNodeId() { return nodeId; }
    public NodeType getNodeType() { return nodeType; }

    public StepStatus getStatus() { return status; }
    public void setStatus(StepStatus status) { this.status = status; }

    public int getSequenceIndex() { return sequenceIndex; }
    public String getResolvedInput() { return resolvedInput; }
    
    public void setResolvedInput(String resolvedInput) {
		this.resolvedInput = resolvedInput;
	}

	public String getOutput() { return output; }
    public int getAttemptCount() { return attemptCount; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer promptTokens) { this.promptTokens = promptTokens; }

    public Integer getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Integer completionTokens) { this.completionTokens = completionTokens; }

    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getErrorMessage() { return errorMessage; }
}
