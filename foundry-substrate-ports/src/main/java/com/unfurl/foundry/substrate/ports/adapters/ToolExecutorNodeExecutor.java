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
/**
 * class for the Foundry AI substrate surface; documents the ToolExecutorNodeExecutor contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public final class ToolExecutorNodeExecutor implements NodeExecutor {
    private final ToolExecutor executor;

/**
 * Constructs ToolExecutorNodeExecutor with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public ToolExecutorNodeExecutor(ToolExecutor executor) {
        this.executor = executor;
    }

/**
 * Performs the execute operation for this component, translating validated inputs into the domain result expected by callers.
 */
    @Override
    public NodeExecutionResult execute(NodeExecutionRequest request, ExecutionContext context) {
        String toolName = stringValue(request.input().getOrDefault("toolName",
                request.node() == null ? null : request.node().uses()));
        String callId = stringValue(request.input().getOrDefault("callId", request.executionId()));
        Map<String, Object> arguments = mapValue(request.input().get("arguments"), request.input());
        ToolCallResult result = executor.execute(new ToolCallRequest(callId, toolName, arguments, request.metadata()), context);
        if (!result.success()) {
            // Adapter boundary: NodeExecutionResult retains the compatibility aliases while
            // ToolCallResult.failure() remains the canonical structured representation.
            return NodeExecutionResult.failed(result.errorCode(), result.errorMessage());
        }
        return NodeExecutionResult.completed(result.output());
    }

/**
 * Implements the stringValue helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

/**
 * Implements the mapValue helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value, Map<String, Object> fallback) {
        if (value instanceof Map<?, ?>) {
            return (Map<String, Object>) value;
        }
        return fallback;
    }
}
