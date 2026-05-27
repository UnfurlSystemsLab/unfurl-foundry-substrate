package com.unfurl.foundry.substrate.rag;

import java.util.Map;

public record RagQuery(
        String query,
        int topK,
        Map<String, Object> filters,
        String collectionRef,
        Map<String, Object> metadata
) {
    public RagQuery {
        filters = filters == null ? Map.of() : Map.copyOf(filters);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if (topK < 0) {
            throw new IllegalArgumentException("topK must be >= 0");
        }
    }
}
