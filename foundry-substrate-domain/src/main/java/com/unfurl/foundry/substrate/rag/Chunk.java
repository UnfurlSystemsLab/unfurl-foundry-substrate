package com.unfurl.foundry.substrate.rag;

import java.util.Map;

/**
 * record for the Foundry AI substrate surface; documents the Chunk contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public record Chunk(
        String id,
        String text,
        double score,
        ChunkSource source,
        Map<String, Object> metadata
) {
/**
 * Constructs Chunk with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public Chunk {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
