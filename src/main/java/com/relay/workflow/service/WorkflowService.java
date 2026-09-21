package com.relay.workflow.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.relay.workflow.exception.WorkflowNotFoundException;
import com.relay.workflow.graph.GraphParser;
import com.relay.workflow.graph.WorkflowGraph;
import com.relay.workflow.model.Workflow;
import com.relay.workflow.model.WorkflowStatus;
import com.relay.workflow.model.WorkflowVersion;
import com.relay.workflow.repository.WorkflowRepository;
import com.relay.workflow.repository.WorkflowVersionRepository;


@Service
public class WorkflowService {
	
	private final WorkflowRepository workflowRepository;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final GraphParser graphParser;
    private final SecureRandom secureRandom = new SecureRandom();

    public WorkflowService(WorkflowRepository workflowRepository,
                            WorkflowVersionRepository workflowVersionRepository,
                            GraphParser graphParser) {
        this.workflowRepository = workflowRepository;
        this.workflowVersionRepository = workflowVersionRepository;
        this.graphParser = graphParser;
    }

    /**
     * Creates a new workflow in DRAFT status. The definition is parsed
     * (so malformed JSON is rejected immediately) but NOT fully validated -
     * drafts are allowed to be incomplete while being worked on. Full
     * structural validation only happens at publish time.
     */
    @Transactional
    public Workflow createDraft(String name, String definitionJson) {
        graphParser.parse(definitionJson); // fail fast on malformed JSON
        String webhookSecret = generateWebhookSecret();
        Workflow workflow = new Workflow(name, definitionJson, webhookSecret);
        return workflowRepository.save(workflow);
    }

    /**
     * Overwrites a workflow's draft definition. Editable regardless of
     * whether the workflow currently has a published version - editing
     * the draft never changes what's already published; only publish()
     * freezes a new snapshot.
     */
    @Transactional
    public Workflow updateDraftDefinition(Long workflowId, String definitionJson) {
        graphParser.parse(definitionJson); // fail fast on malformed JSON
        Workflow workflow = getWorkflowOrThrow(workflowId);
        workflow.setDraftDefinition(definitionJson);
        workflow.touch();
        return workflowRepository.save(workflow);
    }

    /**
     * Validates the current draft definition and, if valid, freezes it
     * into a new immutable WorkflowVersion. The workflow's status flips
     * to PUBLISHED and its publishedVersionId points at the new snapshot.
     * Throws WorkflowValidationException if the draft is structurally
     * invalid - nothing is persisted in that case.
     */
    @Transactional
    public WorkflowVersion publish(Long workflowId) {
        Workflow workflow = getWorkflowOrThrow(workflowId);

        WorkflowGraph graph = graphParser.parse(workflow.getDraftDefinition());
        graphParser.validate(graph);

        int nextVersionNumber = workflowVersionRepository
                .findTopByWorkflowIdOrderByVersionNumberDesc(workflowId)
                .map(v -> v.getVersionNumber() + 1)
                .orElse(1);

        WorkflowVersion version = new WorkflowVersion(
                workflowId, nextVersionNumber, workflow.getDraftDefinition());
        version = workflowVersionRepository.save(version);

        workflow.setStatus(WorkflowStatus.PUBLISHED);
        workflow.setPublishedVersionId(version.getId());
        workflow.touch();
        workflowRepository.save(workflow);

        return version;
    }

    /**
     * Returns the frozen, runnable graph for a workflow's currently
     * published version. This is what the trigger/engine layer calls -
     * runs always execute against a published snapshot, never the draft.
     */
    @Transactional(readOnly = true)
    public WorkflowGraph getPublishedGraph(Long workflowId) {
        Workflow workflow = getWorkflowOrThrow(workflowId);
        if (workflow.getStatus() != WorkflowStatus.PUBLISHED || workflow.getPublishedVersionId() == null) {
            throw new IllegalStateException("Workflow " + workflowId + " has no published version to run.");
        }
        WorkflowVersion version = workflowVersionRepository.findById(workflow.getPublishedVersionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Published version " + workflow.getPublishedVersionId() + " is missing."));
        return graphParser.parse(version.getDefinition());
    }

    @Transactional(readOnly = true)
    public Workflow getWorkflowOrThrow(Long workflowId) {
        return workflowRepository.findById(workflowId)
                .orElseThrow(() -> new WorkflowNotFoundException(workflowId));
    }

    @Transactional(readOnly = true)
    public List<Workflow> listWorkflows() {
        return workflowRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<WorkflowVersion> listVersions(Long workflowId) {
        return workflowVersionRepository.findByWorkflowIdOrderByVersionNumberDesc(workflowId);
    }

    private String generateWebhookSecret() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}
