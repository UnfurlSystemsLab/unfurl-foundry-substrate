package com.unfurl.foundry.substrate.ports.adapters;

import com.unfurl.foundry.substrate.model.Message;
import com.unfurl.foundry.substrate.model.ModelRequest;
import com.unfurl.foundry.substrate.model.ModelResponse;
import com.unfurl.foundry.substrate.ports.ModelProvider;
import com.unfurl.substrate.policy.ExecutionContext;
import com.unfurl.substrate.ports.NodeExecutionRequest;
import com.unfurl.substrate.ports.NodeExecutionResult;
import com.unfurl.substrate.ports.NodeExecutor;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Substrate node adapter for the canonical {@code provider.call} AI capability. */
/**
 * class for the Foundry AI substrate surface; documents the ModelProviderNodeExecutor contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public final class ModelProviderNodeExecutor implements NodeExecutor {
    private final ModelProvider provider;

/**
 * Constructs ModelProviderNodeExecutor with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public ModelProviderNodeExecutor(ModelProvider provider) {
        this.provider = provider;
    }

/**
 * Performs the execute operation for this component, translating validated inputs into the domain result expected by callers.
 */
    @Override
    public NodeExecutionResult execute(NodeExecutionRequest request, ExecutionContext context) {
        ModelRequest modelRequest = request.input().get("request") instanceof ModelRequest existing
                ? existing
                : new ModelRequest(
                messages(request.input().get("messages")),
                stringValue(request.input().getOrDefault("modelRef", request.node() == null ? null : request.node().uses())),
                mapValue(request.input().get("parameters")),
                toolSchemas(request.input().get("toolSchemas")),
                request.metadata());
        ModelResponse response = provider.complete(modelRequest, context);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("message", response.message());
        output.put("toolCalls", response.toolCalls());
        output.put("finishReason", response.finishReason());
        output.put("outcome", response.outcome().name());
        output.put("usage", response.usage());
        if (response.providerName() != null) {
            output.put("providerName", response.providerName());
        }
        output.put("estimatedCostUsd", response.estimatedCostUsd());
        output.put("metadata", response.metadata());
        return NodeExecutionResult.completed(output);
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
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

/**
 * Implements the messages helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    @SuppressWarnings("unchecked")
    private List<Message> messages(Object value) {
        return value instanceof List<?> ? (List<Message>) value : List.of();
    }

/**
 * Implements the toolSchemas helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toolSchemas(Object value) {
        return value instanceof List<?> ? (List<Map<String, Object>>) value : List.of();
    }
}
