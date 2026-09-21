package com.relay.workflow.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.workflow.exception.WorkflowValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GraphParserTest {

    private final GraphParser graphParser = new GraphParser(new ObjectMapper());

    @Test
    void validate_passesForValidLinearGraph() {
        String json = """
            {"entryNodeId":"n1","nodes":[
              {"id":"n1","type":"NOTIFY","config":{}},
              {"id":"n2","type":"NOTIFY","config":{}}
            ],"edges":[{"fromNodeId":"n1","toNodeId":"n2"}]}
            """;
        WorkflowGraph graph = graphParser.parse(json);
        assertDoesNotThrow(() -> graphParser.validate(graph));
    }

    @Test
    void validate_throwsWhenEntryNodeMissing() {
        String json = """
            {"entryNodeId":"does-not-exist","nodes":[{"id":"n1","type":"NOTIFY","config":{}}],"edges":[]}
            """;
        WorkflowGraph graph = graphParser.parse(json);
        WorkflowValidationException ex = assertThrows(WorkflowValidationException.class,
                () -> graphParser.validate(graph));
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("entryNodeId")));
    }

    @Test
    void validate_throwsOnDanglingEdge() {
        String json = """
            {"entryNodeId":"n1","nodes":[{"id":"n1","type":"NOTIFY","config":{}}],
             "edges":[{"fromNodeId":"n1","toNodeId":"ghost"}]}
            """;
        WorkflowGraph graph = graphParser.parse(json);
        WorkflowValidationException ex = assertThrows(WorkflowValidationException.class,
                () -> graphParser.validate(graph));
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("ghost")));
    }

    @Test
    void validate_throwsOnUnreachableNode() {
        String json = """
            {"entryNodeId":"n1","nodes":[
              {"id":"n1","type":"NOTIFY","config":{}},
              {"id":"orphan","type":"NOTIFY","config":{}}
            ],"edges":[]}
            """;
        WorkflowGraph graph = graphParser.parse(json);
        WorkflowValidationException ex = assertThrows(WorkflowValidationException.class,
                () -> graphParser.validate(graph));
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("orphan")));
    }

    @Test
    void validate_throwsOnDuplicateNodeId() {
        String json = """
            {"entryNodeId":"n1","nodes":[
              {"id":"n1","type":"NOTIFY","config":{}},
              {"id":"n1","type":"NOTIFY","config":{}}
            ],"edges":[]}
            """;
        WorkflowGraph graph = graphParser.parse(json);
        WorkflowValidationException ex = assertThrows(WorkflowValidationException.class,
                () -> graphParser.validate(graph));
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.contains("Duplicate")));
    }

    @Test
    void parse_throwsOnMalformedJson() {
        assertThrows(WorkflowValidationException.class, () -> graphParser.parse("{not valid json"));
    }
}
