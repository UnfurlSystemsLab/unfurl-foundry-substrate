package com.unfurl.foundry.substrate.rag;

import java.util.Map;

/** Provenance for a retrieved chunk. */
public record ChunkSource(
        String documentId,
        String uri,
        String location,
        Map<String, Object> metadata
) {
    public ChunkSource {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
