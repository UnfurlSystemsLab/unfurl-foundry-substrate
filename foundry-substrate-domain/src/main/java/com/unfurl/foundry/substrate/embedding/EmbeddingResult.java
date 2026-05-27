package com.unfurl.foundry.substrate.embedding;

import com.unfurl.foundry.substrate.model.ModelUsage;

import java.util.List;
import java.util.Map;

public record EmbeddingResult(
        List<float[]> vectors,
        ModelUsage usage,
        Map<String, Object> metadata
) {
    public EmbeddingResult {
        vectors = vectors == null ? List.of() : List.copyOf(vectors);
        usage = usage == null ? ModelUsage.zero() : usage;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
