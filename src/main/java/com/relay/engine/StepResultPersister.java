package com.relay.engine;

import com.relay.workflow.graph.EdgeDefinition;
import com.relay.workflow.graph.GraphParser;
import com.relay.workflow.graph.NodeDefinition;
import com.relay.workflow.graph.WorkflowGraph;
import com.relay.workflow.model.*;
import com.relay.workflow.repository.ApprovalRepository;
import com.relay.workflow.repository.RunRepository;
import com.relay.workflow.repository.StepRepository;
import com.relay.workflow.repository.WorkflowVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class StepResultPersister {

    private static final Logger log = LoggerFactory.getLogger(StepResultPersister.class);

    private final StepRepository stepRepository;
    private final RunRepository runRepository;
    private final ApprovalRepository approvalRepository;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final GraphParser graphParser;

    public StepResultPersister(StepRepository stepRepository,
                                RunRepository runRepository,
                                ApprovalRepository approvalRepository,
                                WorkflowVersionRepository workflowVersionRepository,
                                GraphParser graphParser) {
        this.stepRepository = stepRepository;
        this.runRepository = runRepository;
        this.approvalRepository = approvalRepository;
        this.workflowVersionRepository = workflowVersionRepository;
        this.graphParser = graphParser;
    }

    @Transactional
    public void persistSuccess(Long stepId, NodeExecutionResult result) {
        Step step = stepRepository.findById(stepId)
                .orElseThrow(() -> new IllegalStateException("Step not found: " + stepId));

        if (result.getPromptTokens() != null) {
            step.setPromptTokens(result.getPromptTokens());
        }
        if (result.getCompletionTokens() != null) {
            step.setCompletionTokens(result.getCompletionTokens());
        }
        step.markCompleted(result.getOutputJson());
        stepRepository.save(step);

        Run run = runRepository.findById(step.getRunId())
                .orElseThrow(() -> new IllegalStateException("Run not found: " + step.getRunId()));
        run.incrementStepsExecuted();

        if (run.hasExceededStepCap()) {
            run.setStatus(RunStatus.FAILED);
            run.setFailureReason("Run exceeded max step cap of " + run.getMaxSteps());
            runRepository.save(run);
            log.warn("Run {} halted: step cap exceeded", run.getId());
            return;
        }

        WorkflowGraph graph = graphParser.parse(loadVersionDefinition(run.getWorkflowVersionId()));
        Optional<EdgeDefinition> nextEdge = resolveNextEdge(graph, step.getNodeId(), result.getBranch());

        if (nextEdge.isEmpty()) {
            run.setStatus(RunStatus.COMPLETED);
            runRepository.save(run);
            log.info("Run {} completed - no outgoing edge from node {}", run.getId(), step.getNodeId());
            return;
        }

        NodeDefinition nextNode = findNode(graph, nextEdge.get().getToNodeId());

        Step nextStep = new Step(
                run.getId(),
                nextNode.getId(),
                nextNode.getType(),
                step.getSequenceIndex() + 1,
                result.getOutputJson()
        );
        nextStep.setIdempotencyKey(UUID.randomUUID().toString());
        stepRepository.save(nextStep);
        runRepository.save(run);
    }

    @Transactional
    public void persistFailure(Long stepId, String errorMessage) {
        Step step = stepRepository.findById(stepId)
                .orElseThrow(() -> new IllegalStateException("Step not found: " + stepId));
        step.markFailed(errorMessage);
        stepRepository.save(step);

        Run run = runRepository.findById(step.getRunId())
                .orElseThrow(() -> new IllegalStateException("Run not found: " + step.getRunId()));
        run.setStatus(RunStatus.FAILED);
        run.setFailureReason("Step " + step.getNodeId() + " failed: " + errorMessage);
        runRepository.save(run);

        log.error("Run {} failed at node {}: {}", run.getId(), step.getNodeId(), errorMessage);
    }

    @Transactional
    public void pauseForApproval(Long stepId) {
        Step step = stepRepository.findById(stepId)
                .orElseThrow(() -> new IllegalStateException("Step not found: " + stepId));
        Run run = runRepository.findById(step.getRunId())
                .orElseThrow(() -> new IllegalStateException("Run not found: " + step.getRunId()));

        step.setStatus(StepStatus.WAITING_APPROVAL);
        stepRepository.save(step);

        run.setStatus(RunStatus.WAITING_APPROVAL);
        runRepository.save(run);

        if (approvalRepository.findByStepId(step.getId()).isEmpty()) {
            approvalRepository.save(new Approval(run.getId(), step.getId()));
        }
        log.info("Run {} paused for approval at node {}", run.getId(), step.getNodeId());
    }

    @Transactional
    public void recordResolvedInput(Long stepId, String resolvedInputJson) {
        Step step = stepRepository.findById(stepId)
                .orElseThrow(() -> new IllegalStateException("Step not found: " + stepId));
        step.setResolvedInput(resolvedInputJson);
        stepRepository.save(step);
    }

    private Optional<EdgeDefinition> resolveNextEdge(WorkflowGraph graph, String fromNodeId, String branch) {
        List<EdgeDefinition> candidates = graph.getEdges().stream()
                .filter(e -> e.getFromNodeId().equals(fromNodeId))
                .toList();

        if (branch != null) {
            return candidates.stream().filter(e -> branch.equals(e.getBranch())).findFirst();
        }
        return candidates.stream().findFirst();
    }

    private NodeDefinition findNode(WorkflowGraph graph, String nodeId) {
        return graph.getNodes().stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Node not found in graph: " + nodeId));
    }

    private String loadVersionDefinition(Long workflowVersionId) {
        return workflowVersionRepository.findById(workflowVersionId)
                .orElseThrow(() -> new IllegalStateException("Workflow version not found: " + workflowVersionId))
                .getDefinition();
    }
}