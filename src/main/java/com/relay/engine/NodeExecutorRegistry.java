package com.relay.engine;

import com.relay.workflow.model.NodeType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class NodeExecutorRegistry {

    private final Map<NodeType, NodeExecutor> executorsByType = new EnumMap<>(NodeType.class);

    public NodeExecutorRegistry(List<NodeExecutor> executors) {
        for (NodeExecutor executor : executors) {
            executorsByType.put(executor.getSupportedType(), executor);
        }
    }

    public Optional<NodeExecutor> findExecutor(NodeType type) {
        return Optional.ofNullable(executorsByType.get(type));
    }
}
