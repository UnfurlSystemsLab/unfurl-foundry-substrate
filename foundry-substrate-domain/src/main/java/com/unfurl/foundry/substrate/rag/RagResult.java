package com.unfurl.foundry.substrate.rag;

import java.util.List;
import java.util.Map;

public record RagResult(
        List<Chunk> chunks,
        Map<String, Object> metadata
) {
    public RagResult {
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static RagResult empty() {
        return new RagResult(List.of(), Map.of());
    }
}
