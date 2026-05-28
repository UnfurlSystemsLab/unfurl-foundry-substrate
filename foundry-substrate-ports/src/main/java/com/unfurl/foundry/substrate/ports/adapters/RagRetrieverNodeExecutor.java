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
public final class RagRetrieverNodeExecutor implements NodeExecutor {
    private final RagRetriever retriever;

    public RagRetrieverNodeExecutor(RagRetriever retriever) {
        this.retriever = retriever;
    }

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

    private int intValue(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }
}
