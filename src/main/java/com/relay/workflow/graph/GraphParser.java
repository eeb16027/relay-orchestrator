package com.relay.workflow.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relay.workflow.exception.WorkflowValidationException;




/**
 * Deserializes the raw JSON `definition` column into a WorkflowGraph and
 * validates its structure before a workflow is allowed to publish.
 *
 * Validation here is purely structural (does the graph make sense as a
 * graph?). It does NOT enforce runtime rules like "approval must exist
 * before a sensitive node executes" - that is the engine's job at
 * execution time, per the requirement that approval gates are enforced
 * by the engine, not by static config.
 */
@Component
public class GraphParser {
	
	private final ObjectMapper objectMapper;

    public GraphParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public WorkflowGraph parse(String definitionJson) {
        try {
            return objectMapper.readValue(definitionJson, WorkflowGraph.class);
        } catch (JsonProcessingException e) {
            throw new WorkflowValidationException(
                    List.of("Definition is not valid JSON: " + e.getOriginalMessage()));
        }
    }

    public String serialize(WorkflowGraph graph) {
        try {
            return objectMapper.writeValueAsString(graph);
        } catch (JsonProcessingException e) {
            // Serialization of our own DTO should never fail; wrap as unchecked
            throw new IllegalStateException("Failed to serialize workflow graph", e);
        }
    }

    /**
     * Runs full structural validation. Throws WorkflowValidationException
     * with every problem found if the graph is invalid; returns silently
     * if valid.
     */
    public void validate(WorkflowGraph graph) {
        List<String> errors = new ArrayList<>();

        if (graph.getNodes() == null || graph.getNodes().isEmpty()) {
            errors.add("Workflow must contain at least one node.");
            throw new WorkflowValidationException(errors);
        }

        Set<String> nodeIds = new HashSet<>();
        for (NodeDefinition node : graph.getNodes()) {
            if (node.getId() == null || node.getId().isBlank()) {
                errors.add("Every node must have a non-blank id.");
                continue;
            }
            if (!nodeIds.add(node.getId())) {
                errors.add("Duplicate node id found: " + node.getId());
            }
            if (node.getType() == null) {
                errors.add("Node '" + node.getId() + "' is missing a type.");
            }
        }

        // Entry node must exist among the declared nodes
        if (graph.getEntryNodeId() == null || graph.getEntryNodeId().isBlank()) {
            errors.add("Workflow must declare an entryNodeId.");
        } else if (!nodeIds.contains(graph.getEntryNodeId())) {
            errors.add("entryNodeId '" + graph.getEntryNodeId() + "' does not match any declared node.");
        }

        // Every edge must reference nodes that actually exist
        List<EdgeDefinition> edges = graph.getEdges() == null ? List.of() : graph.getEdges();
        for (EdgeDefinition edge : edges) {
            if (!nodeIds.contains(edge.getFromNodeId())) {
                errors.add("Edge references unknown fromNodeId: " + edge.getFromNodeId());
            }
            if (!nodeIds.contains(edge.getToNodeId())) {
                errors.add("Edge references unknown toNodeId: " + edge.getToNodeId());
            }
        }

        // Every node (other than the entry node) must be reachable by
        // walking forward from the entry node - otherwise it's dead config
        // that could silently never run.
        if (errors.isEmpty()) {
            Set<String> reachable = computeReachableNodeIds(graph.getEntryNodeId(), edges);
            for (String nodeId : nodeIds) {
                if (!reachable.contains(nodeId)) {
                    errors.add("Node '" + nodeId + "' is not reachable from the entry node.");
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new WorkflowValidationException(errors);
        }
    }

    private Set<String> computeReachableNodeIds(String entryNodeId, List<EdgeDefinition> edges) {
        Map<String, List<String>> adjacency = new HashMap<>();
        for (EdgeDefinition edge : edges) {
            adjacency.computeIfAbsent(edge.getFromNodeId(), k -> new ArrayList<>()).add(edge.getToNodeId());
        }

        Set<String> visited = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(entryNodeId);

        while (!stack.isEmpty()) {
            String current = stack.pop();
            if (visited.add(current)) {
                for (String next : adjacency.getOrDefault(current, List.of())) {
                    stack.push(next);
                }
            }
        }
        return visited;
    }

}
