package com.relay.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relay.workflow.model.Run;
import com.relay.workflow.model.Step;
import com.relay.workflow.model.StepStatus;
import com.relay.workflow.repository.StepRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RunContextBuilder {

    private final ObjectMapper objectMapper;
    private final StepRepository stepRepository;

    public RunContextBuilder(ObjectMapper objectMapper, StepRepository stepRepository) {
        this.objectMapper = objectMapper;
        this.stepRepository = stepRepository;
    }

    public JsonNode build(Run run) {
        ObjectNode root = objectMapper.createObjectNode();

        JsonNode trigger = parseOrNull(run.getTriggerPayload());
        root.set("trigger", trigger != null ? trigger : objectMapper.createObjectNode());

        ObjectNode stepsNode = objectMapper.createObjectNode();
        List<Step> steps = stepRepository.findByRunIdOrderBySequenceIndexAsc(run.getId());
        for (Step s : steps) {
            if (s.getStatus() == StepStatus.COMPLETED && s.getOutput() != null) {
                ObjectNode stepEntry = objectMapper.createObjectNode();
                JsonNode output = parseOrNull(s.getOutput());
                stepEntry.set("output", output != null ? output : objectMapper.createObjectNode());
                stepsNode.set(s.getNodeId(), stepEntry);
            }
        }
        root.set("steps", stepsNode);
        return root;
    }

    private JsonNode parseOrNull(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}