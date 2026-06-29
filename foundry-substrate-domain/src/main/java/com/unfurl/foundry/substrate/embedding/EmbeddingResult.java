package com.unfurl.foundry.substrate.embedding;

import com.unfurl.foundry.substrate.model.ModelUsage;

import java.util.List;
import java.util.Map;

/**
 * record for the Foundry AI substrate surface; documents the EmbeddingResult contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public record EmbeddingResult(
        List<float[]> vectors,
        ModelUsage usage,
        Map<String, Object> metadata
) {
/**
 * Constructs EmbeddingResult with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public EmbeddingResult {
        vectors = vectors == null ? List.of() : List.copyOf(vectors);
        usage = usage == null ? ModelUsage.zero() : usage;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
