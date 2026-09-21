package com.relay.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.workflow.exception.WorkflowNotFoundException;
import com.relay.workflow.graph.NodeDefinition;
import com.relay.workflow.graph.WorkflowGraph;
import com.relay.workflow.model.*;
import com.relay.workflow.repository.RunRepository;
import com.relay.workflow.repository.StepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class RunService {

    private final WorkflowService workflowService;
    private final RunRepository runRepository;
    private final StepRepository stepRepository;
    private final ObjectMapper objectMapper;
    private final int defaultMaxSteps;

    public RunService(WorkflowService workflowService,
                       RunRepository runRepository,
                       StepRepository stepRepository,
                       ObjectMapper objectMapper,
                       @org.springframework.beans.factory.annotation.Value(
                               "${relay.engine.run.default-max-steps:100}") int defaultMaxSteps) {
        this.workflowService = workflowService;
        this.runRepository = runRepository;
        this.stepRepository = stepRepository;
        this.objectMapper = objectMapper;
        this.defaultMaxSteps = defaultMaxSteps;
    }

    /**
     * Starts a new run against a workflow's currently PUBLISHED version.
     * Only the entry node's Step row is created here, in PENDING status.
     * Subsequent steps are created by the engine as it walks edges -
     * we can't know the full path upfront because CONDITION nodes branch
     * based on runtime output.
     */
    @Transactional
    public Run startRun(Long workflowId, ObjectNode triggerPayload) {
        Workflow workflow = workflowService.getWorkflowOrThrow(workflowId);
        WorkflowGraph graph = workflowService.getPublishedGraph(workflowId);

        NodeDefinition entryNode = findNode(graph, graph.getEntryNodeId());

        String payloadJson = writeJson(triggerPayload);

        Run run = new Run(
                workflowId,
                workflow.getPublishedVersionId(),
                payloadJson,
                defaultMaxSteps
        );
        run = runRepository.save(run);

        // The entry node's resolved input is just the raw trigger payload -
        // downstream nodes will resolve their inputs from prior step outputs
        // once the engine implements template resolution.
        Step entryStep = new Step(
                run.getId(),
                entryNode.getId(),
                entryNode.getType(),
                0,
                payloadJson
        );
        stepRepository.save(entryStep);

        return run;
    }

    @Transactional(readOnly = true)
    public Run getRunOrThrow(Long runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Run not found with id: " + runId));
    }

    private NodeDefinition findNode(WorkflowGraph graph, String nodeId) {
        return graph.getNodes().stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Entry node '" + nodeId + "' not found in published graph - " +
                        "this should have been caught at publish validation."));
    }

    private String writeJson(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalArgumentException("Trigger payload could not be serialized", e);
        }
    }
}
