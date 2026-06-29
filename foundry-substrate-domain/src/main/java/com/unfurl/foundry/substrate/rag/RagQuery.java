package com.unfurl.foundry.substrate.rag;

import java.util.Map;

/**
 * record for the Foundry AI substrate surface; documents the RagQuery contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public record RagQuery(
        String query,
        int topK,
        Map<String, Object> filters,
        String collectionRef,
        Map<String, Object> metadata
) {
/**
 * Constructs RagQuery with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public RagQuery {
        filters = filters == null ? Map.of() : Map.copyOf(filters);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if (topK < 0) {
            throw new IllegalArgumentException("topK must be >= 0");
        }
    }
}
