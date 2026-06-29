package com.unfurl.foundry.substrate.rag;

import java.util.Map;

/** Provenance for a retrieved chunk. */
/**
 * record for the Foundry AI substrate surface; documents the ChunkSource contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public record ChunkSource(
        String documentId,
        String uri,
        String location,
        Map<String, Object> metadata
) {
/**
 * Constructs ChunkSource with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public ChunkSource {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
