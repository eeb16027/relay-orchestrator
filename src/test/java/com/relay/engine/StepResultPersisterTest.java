package com.relay.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.workflow.graph.GraphParser;
import com.relay.workflow.model.*;
import com.relay.workflow.repository.ApprovalRepository;
import com.relay.workflow.repository.RunRepository;
import com.relay.workflow.repository.StepRepository;
import com.relay.workflow.repository.WorkflowVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StepResultPersisterTest {

    @Mock private StepRepository stepRepository;
    @Mock private RunRepository runRepository;
    @Mock private ApprovalRepository approvalRepository;
    @Mock private WorkflowVersionRepository workflowVersionRepository;
    @Mock private GraphParser graphParser;

    private StepResultPersister persister;

    @BeforeEach
    void setUp() {
        persister = new StepResultPersister(stepRepository, runRepository, approvalRepository,
                workflowVersionRepository, graphParser);
    }

    @Test
    void persistSuccess_haltsRunWhenStepCapExceeded() {
        Step step = new Step(10L, "n1", NodeType.NOTIFY, 0, "{}");
        Run run = new Run(1L, 5L, "{}", 1); // maxSteps = 1

        when(stepRepository.findById(100L)).thenReturn(Optional.of(step));
        when(runRepository.findById(10L)).thenReturn(Optional.of(run));

        persister.persistSuccess(100L, new NodeExecutionResult("{}"));

        assertEquals(RunStatus.FAILED, run.getStatus());
        assertTrue(run.getFailureReason().contains("step cap"));
        verify(stepRepository, times(1)).save(any(Step.class)); // only the completed step, no next step
    }

    @Test
    void persistSuccess_createsNextStepWithFreshIdempotencyKeyDistinctFromCurrent() {
        Step step = new Step(10L, "n1", NodeType.NOTIFY, 0, "{}");
        step.setIdempotencyKey("original-key");

        Run run = new Run(1L, 5L, "{}", 100);

        String definitionJson = """
            {"entryNodeId":"n1","nodes":[
              {"id":"n1","type":"NOTIFY","config":{}},
              {"id":"n2","type":"NOTIFY","config":{}}
            ],"edges":[{"fromNodeId":"n1","toNodeId":"n2"}]}
            """;
        WorkflowVersion version = new WorkflowVersion(1L, 1, definitionJson);

        when(stepRepository.findById(100L)).thenReturn(Optional.of(step));
        when(runRepository.findById(10L)).thenReturn(Optional.of(run));
        when(workflowVersionRepository.findById(5L)).thenReturn(Optional.of(version));

        GraphParser realParser = new GraphParser(new ObjectMapper());
        when(graphParser.parse(definitionJson)).thenReturn(realParser.parse(definitionJson));

        persister.persistSuccess(100L, new NodeExecutionResult("{}"));

        verify(stepRepository).save(argThat(s ->
                "n2".equals(s.getNodeId()) &&
                s.getIdempotencyKey() != null &&
                !s.getIdempotencyKey().equals("original-key")
        ));
    }
}
