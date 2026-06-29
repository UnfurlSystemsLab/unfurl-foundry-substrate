package com.unfurl.foundry.substrate.rag;

import java.util.List;
import java.util.Map;

/**
 * record for the Foundry AI substrate surface; documents the RagResult contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public record RagResult(
        List<Chunk> chunks,
        Map<String, Object> metadata
) {
/**
 * Constructs RagResult with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public RagResult {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

/**
 * Factory method: creates the empty result while keeping caller-facing defaults and validation in one place.
 */
    public static RagResult empty() {
        return new RagResult(List.of(), Map.of());
    }
}
