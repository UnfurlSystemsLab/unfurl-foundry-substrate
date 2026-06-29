package com.unfurl.foundry.substrate.embedding;

import java.util.List;
import java.util.Map;

/**
 * record for the Foundry AI substrate surface; documents the EmbeddingRequest contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public record EmbeddingRequest(
        List<String> inputs,
        String modelRef,
        Map<String, Object> metadata
) {
/**
 * Constructs EmbeddingRequest with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public EmbeddingRequest {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
