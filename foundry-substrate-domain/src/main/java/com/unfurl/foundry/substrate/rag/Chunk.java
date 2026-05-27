package com.unfurl.foundry.substrate.rag;

import java.util.Map;

public record Chunk(
        String id,
        String text,
        double score,
        ChunkSource source,
        Map<String, Object> metadata
) {
    public Chunk {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
