package com.relay.workflow.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A frozen, runnable snapshot of a workflow's node graph.
 * Created at publish time; never mutated afterward.
 */
@Entity
@Table(name = "workflow_versions")
public class WorkflowVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id", nullable = false)
    private Long workflowId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    // Frozen JSON graph: nodes + edges, resolved and validated at publish time
    @Column(name = "definition", columnDefinition = "JSON", nullable = false)
    private String definition;

    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt = Instant.now();

    protected WorkflowVersion() {
        // JPA
    }

    public WorkflowVersion(Long workflowId, int versionNumber, String definition) {
        this.workflowId = workflowId;
        this.versionNumber = versionNumber;
        this.definition = definition;
    }

    public Long getId() { return id; }
    public Long getWorkflowId() { return workflowId; }
    public int getVersionNumber() { return versionNumber; }
    public String getDefinition() { return definition; }
    public Instant getPublishedAt() { return publishedAt; }
}
