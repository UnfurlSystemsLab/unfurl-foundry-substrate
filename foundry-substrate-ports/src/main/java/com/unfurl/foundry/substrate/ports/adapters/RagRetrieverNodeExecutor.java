package com.unfurl.foundry.substrate.ports.adapters;

import com.unfurl.foundry.substrate.rag.RagQuery;
import com.unfurl.foundry.substrate.rag.RagResult;
import com.unfurl.foundry.substrate.ports.RagRetriever;
import com.unfurl.substrate.policy.ExecutionContext;
import com.unfurl.substrate.ports.NodeExecutionRequest;
import com.unfurl.substrate.ports.NodeExecutionResult;
import com.unfurl.substrate.ports.NodeExecutor;

import java.util.Map;

/** Substrate node adapter for the canonical {@code rag.search} AI capability. */
/**
 * class for the Foundry AI substrate surface; documents the RagRetrieverNodeExecutor contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public final class RagRetrieverNodeExecutor implements NodeExecutor {
    private final RagRetriever retriever;

/**
 * Constructs RagRetrieverNodeExecutor with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public RagRetrieverNodeExecutor(RagRetriever retriever) {
        this.retriever = retriever;
    }

/**
 * Performs the execute operation for this component, translating validated inputs into the domain result expected by callers.
 */
    @Override
    public NodeExecutionResult execute(NodeExecutionRequest request, ExecutionContext context) {
        RagQuery query = request.input().get("query") instanceof RagQuery existing
                ? existing
                : new RagQuery(
                String.valueOf(request.input().getOrDefault("query", "")),
                intValue(request.input().get("topK"), 5),
                mapValue(request.input().get("filters")),
                stringValue(request.input().get("collectionRef")),
                request.metadata());
        RagResult result = retriever.retrieve(query, context);
        return NodeExecutionResult.completed(Map.of("chunks", result.chunks(), "metadata", result.metadata()));
    }

/**
 * Implements the intValue helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private int intValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
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
}
