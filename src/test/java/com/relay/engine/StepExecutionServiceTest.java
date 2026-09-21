package com.relay.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.workflow.graph.GraphParser;
import com.relay.workflow.model.*;
import com.relay.workflow.repository.ApprovalRepository;
import com.relay.workflow.repository.RunRepository;
import com.relay.workflow.repository.WorkflowVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StepExecutionServiceTest {

    @Mock private StepClaimService stepClaimService;
    @Mock private StepResultPersister stepResultPersister;
    @Mock private NodeExecutorRegistry executorRegistry;
    @Mock private RunRepository runRepository;
    @Mock private WorkflowVersionRepository workflowVersionRepository;
    @Mock private ApprovalRepository approvalRepository;
    @Mock private GraphParser graphParser;
    @Mock private RunContextBuilder runContextBuilder;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TemplateResolver templateResolver = new TemplateResolver(objectMapper);

    private StepExecutionService service;

    @BeforeEach
    void setUp() {
        service = new StepExecutionService(stepClaimService, stepResultPersister, executorRegistry,
                runRepository, workflowVersionRepository, approvalRepository, graphParser,
                objectMapper, runContextBuilder, templateResolver);
    }

    @Test
    void claimAndExecuteNextStep_returnsFalseWhenQueueEmpty() {
        when(stepClaimService.claimNextStep()).thenReturn(Optional.empty());
        assertFalse(service.claimAndExecuteNextStep());
        verifyNoInteractions(stepResultPersister, executorRegistry);
    }

    @Test
    void claimAndExecuteNextStep_pausesForApprovalWhenSensitiveActionNotYetApproved() throws Exception {
        Step step = new Step(1L, "n1", NodeType.HTTP_REQUEST, 0, "{}");
        when(stepClaimService.claimNextStep()).thenReturn(Optional.of(step));

        Run run = new Run(1L, 5L, "{}", 100);
        when(runRepository.findById(1L)).thenReturn(Optional.of(run));

        String definitionJson = """
            {"entryNodeId":"n1","nodes":[
              {"id":"n1","type":"HTTP_REQUEST","config":{"url":"http://x","requiresApproval":true}}
            ],"edges":[]}
            """;
        WorkflowVersion version = new WorkflowVersion(1L, 1, definitionJson);
        when(workflowVersionRepository.findById(5L)).thenReturn(Optional.of(version));

        GraphParser realParser = new GraphParser(objectMapper);
        when(graphParser.parse(definitionJson)).thenReturn(realParser.parse(definitionJson));
        when(runContextBuilder.build(run)).thenReturn(objectMapper.readTree("{\"trigger\":{},\"steps\":{}}"));
        when(approvalRepository.findByStepIdAndStatus(any(), eq(ApprovalStatus.APPROVED)))
                .thenReturn(Optional.empty());

        assertTrue(service.claimAndExecuteNextStep());
        verify(stepResultPersister).pauseForApproval(step.getId());
        verifyNoInteractions(executorRegistry);
    }

    @Test
    void claimAndExecuteNextStep_executesNodeWhenNoApprovalRequired() throws Exception {
        Step step = new Step(1L, "n1", NodeType.NOTIFY, 0, "{}");
        when(stepClaimService.claimNextStep()).thenReturn(Optional.of(step));

        Run run = new Run(1L, 5L, "{}", 100);
        when(runRepository.findById(1L)).thenReturn(Optional.of(run));

        String definitionJson = """
            {"entryNodeId":"n1","nodes":[{"id":"n1","type":"NOTIFY","config":{"message":"hi"}}],"edges":[]}
            """;
        WorkflowVersion version = new WorkflowVersion(1L, 1, definitionJson);
        when(workflowVersionRepository.findById(5L)).thenReturn(Optional.of(version));

        GraphParser realParser = new GraphParser(objectMapper);
        when(graphParser.parse(definitionJson)).thenReturn(realParser.parse(definitionJson));
        when(runContextBuilder.build(run)).thenReturn(objectMapper.readTree("{\"trigger\":{},\"steps\":{}}"));

        NodeExecutor mockExecutor = mock(NodeExecutor.class);
        when(executorRegistry.findExecutor(NodeType.NOTIFY)).thenReturn(Optional.of(mockExecutor));
        when(mockExecutor.execute(any())).thenReturn(new NodeExecutionResult("{\"notified\":true}"));

        assertTrue(service.claimAndExecuteNextStep());
        verify(stepResultPersister).persistSuccess(eq(step.getId()), any(NodeExecutionResult.class));
        verify(stepResultPersister, never()).pauseForApproval(any());
    }
}