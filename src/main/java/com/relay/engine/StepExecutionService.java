package com.relay.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.workflow.graph.GraphParser;
import com.relay.workflow.graph.NodeDefinition;
import com.relay.workflow.graph.WorkflowGraph;
import com.relay.workflow.model.Approval;
import com.relay.workflow.model.ApprovalStatus;
import com.relay.workflow.model.NodeType;
import com.relay.workflow.model.Run;
import com.relay.workflow.model.Step;
import com.relay.workflow.repository.ApprovalRepository;
import com.relay.workflow.repository.RunRepository;
import com.relay.workflow.repository.WorkflowVersionRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Coordinates one poll cycle: claim a step, dispatch it to the right
 * executor, persist the outcome. Deliberately NOT @Transactional -
 * executing a node (an HTTP call, later an AI call) can take seconds,
 * and holding a DB transaction open for that whole time would be a
 * connection-pool and locking disaster. The claim and the persist each
 * get their own short transaction in their respective services.
 */
@Service
public class StepExecutionService {

    private static final Logger log = LoggerFactory.getLogger(StepExecutionService.class);

    private final StepClaimService stepClaimService;
    private final StepResultPersister stepResultPersister;
    private final NodeExecutorRegistry executorRegistry;
    private final RunRepository runRepository;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final ApprovalRepository approvalRepository;
    private final GraphParser graphParser;
    private final ObjectMapper objectMapper;
    private final RunContextBuilder runContextBuilder;
    private final TemplateResolver templateResolver;

    public StepExecutionService(StepClaimService stepClaimService,
                                 StepResultPersister stepResultPersister,
                                 NodeExecutorRegistry executorRegistry,
                                 RunRepository runRepository,
                                 WorkflowVersionRepository workflowVersionRepository,
                                 ApprovalRepository approvalRepository,
                                 GraphParser graphParser,
                                 ObjectMapper objectMapper,
                                 RunContextBuilder runContextBuilder,
                                 TemplateResolver templateResolver) {
        this.stepClaimService = stepClaimService;
        this.stepResultPersister = stepResultPersister;
        this.executorRegistry = executorRegistry;
        this.runRepository=runRepository;
        this.workflowVersionRepository=workflowVersionRepository;
        this.approvalRepository=approvalRepository;
        this.graphParser=graphParser;
        this.objectMapper = objectMapper;
        this.runContextBuilder=runContextBuilder;
        this.templateResolver=templateResolver;
    }

    /**
     * Returns true if a step was claimed and processed (whether it
     * succeeded or failed), false if the queue was empty - so the
     * caller knows whether to poll again immediately or wait.
     */
    public boolean claimAndExecuteNextStep() {
        Optional<Step> claimedOpt = stepClaimService.claimNextStep();
        if (claimedOpt.isEmpty()) {
            return false;
        }

        Step step = claimedOpt.get();
        
        NodeExecutionContext context;
        try {
        	context=buildContext(step);
        }
        catch(Exception e) {
        	log.error("step {} failed while building execution context",step.getId(),e);
        	stepResultPersister.persistFailure(step.getId(), "failed to load node context:"+e.getMessage());
        	return true;
        }

        if(requiresApprovalGate(step, context)) {
        	Optional<Approval> approved=approvalRepository.findByStepIdAndStatus(step.getId(), ApprovalStatus.APPROVED);
        	if(approved.isEmpty()) {
        		stepResultPersister.pauseForApproval(step.getId());
        		return true;
        	}
        	if(step.getNodeType()==NodeType.APPROVAL) {
        		stepResultPersister.persistSuccess(step.getId(), new NodeExecutionResult("{\"approved\":true}"));
        		return true;
        	}
        }
        
        Optional<NodeExecutor> executor = executorRegistry.findExecutor(step.getNodeType());
        if (executor.isEmpty()) {
            stepResultPersister.persistFailure(step.getId(),
                    "No executor registered for node type " + step.getNodeType() +
                    " (AI nodes are not implemented yet).");
            return true;
        }

        try {
            //NodeExecutionContext context = buildContext(step);
            NodeExecutionResult result = executor.get().execute(context);
            stepResultPersister.persistSuccess(step.getId(), result);
        } catch (NodeExecutionException e) {
            log.error("Step {} failed: {}", step.getId(), e.getMessage());
            stepResultPersister.persistFailure(step.getId(), e.getMessage());
        } catch (Exception e) {
            log.error("Step {} failed unexpectedly", step.getId(), e);
            stepResultPersister.persistFailure(step.getId(), "Unexpected error: " + e.getMessage());
        }

        return true;
    }
    
    private boolean requiresApprovalGate(Step step,NodeExecutionContext context) {
    	if(step.getNodeType()==NodeType.APPROVAL) {
    		return true;
    	}
    	ObjectNode config=context.getConfig();
    	return config.has("requiresApproval") && config.get("requiresApproval").asBoolean();
    }

    private NodeExecutionContext buildContext(Step step) {
        Run run=runRepository.findById(step.getRunId()).
        		orElseThrow(()->new IllegalStateException("Run not found:"+step.getRunId()));
        String definitionJson=workflowVersionRepository.findById(run.getWorkflowVersionId())
        		.orElseThrow(()->new IllegalStateException("workflow version not found:"+
        				run.getWorkflowVersionId())).getDefinition();
        
        WorkflowGraph graph= graphParser.parse(definitionJson);
        
        NodeDefinition nodeDefinition=graph.getNodes().stream().
        		filter(n->n.getId().equals(step.getNodeId())).findFirst()
        		.orElseThrow(()->new IllegalStateException("Node '"+step.getNodeId()+
        				"' not found in workflow version" +run.getWorkflowVersionId()));
        
        ObjectNode rawconfig=nodeDefinition.getConfig()!=null?
        		nodeDefinition.getConfig():objectMapper.createObjectNode();
        JsonNode runData=runContextBuilder.build(run);
        ObjectNode resolvedCofig=templateResolver.resolve(rawconfig, runData);
        
        stepResultPersister.recordResolvedInput(step.getId(), resolvedCofig.toString());
        return new NodeExecutionContext(
                step.getRunId(), step.getId(), step.getNodeId(), step.getNodeType(),
                runData.toString(),resolvedCofig,
                step.getIdempotencyKey()
        );
    }
}
