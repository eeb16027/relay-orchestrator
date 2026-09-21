package com.relay.workflow.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "workflows")
public class Workflow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkflowStatus status = WorkflowStatus.DRAFT;

    // Points to the currently published version (null while still draft-only)
    @Column(name = "published_version_id")
    private Long publishedVersionId;

    // Latest draft definition, editable while status == DRAFT
    @Column(name = "draft_definition", columnDefinition = "JSON")
    private String draftDefinition;

    @Column(name = "webhook_secret")
    private String webhookSecret;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Workflow() {
        // JPA
    }

    public Workflow(String name, String draftDefinition, String webhookSecret) {
        this.name = name;
        this.draftDefinition = draftDefinition;
        this.webhookSecret = webhookSecret;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    // --- getters / setters ---

    public Long getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public WorkflowStatus getStatus() { return status; }
    public void setStatus(WorkflowStatus status) { this.status = status; }

    public Long getPublishedVersionId() { return publishedVersionId; }
    public void setPublishedVersionId(Long publishedVersionId) { this.publishedVersionId = publishedVersionId; }

    public String getDraftDefinition() { return draftDefinition; }
    public void setDraftDefinition(String draftDefinition) { this.draftDefinition = draftDefinition; }

    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
