package com.unfurl.foundry.substrate.ports.adapters;

import com.unfurl.foundry.substrate.ports.ToolCallRequest;
import com.unfurl.foundry.substrate.ports.ToolCallResult;
import com.unfurl.foundry.substrate.ports.ToolExecutor;
import com.unfurl.substrate.policy.ExecutionContext;
import com.unfurl.substrate.ports.NodeExecutionRequest;
import com.unfurl.substrate.ports.NodeExecutionResult;
import com.unfurl.substrate.ports.NodeExecutor;

import java.util.Map;

/** Substrate node adapter for the canonical {@code tool.call} AI capability. */
public final class ToolExecutorNodeExecutor implements NodeExecutor {
    private final ToolExecutor executor;

    public ToolExecutorNodeExecutor(ToolExecutor executor) {
        this.executor = executor;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionRequest request, ExecutionContext context) {
        String toolName = stringValue(request.input().getOrDefault("toolName",
                request.node() == null ? null : request.node().uses()));
        String callId = stringValue(request.input().getOrDefault("callId", request.executionId()));
        Map<String, Object> arguments = mapValue(request.input().get("arguments"), request.input());
        ToolCallResult result = executor.execute(new ToolCallRequest(callId, toolName, arguments, request.metadata()), context);
        if (!result.success()) {
            return NodeExecutionResult.failed(result.errorCode(), result.errorMessage());
        }
        return NodeExecutionResult.completed(result.output());
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value, Map<String, Object> fallback) {
        if (value instanceof Map<?, ?>) {
            return (Map<String, Object>) value;
        }
        return fallback;
    }
}
