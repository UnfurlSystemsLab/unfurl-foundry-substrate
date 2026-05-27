package com.unfurl.foundry.substrate.embedding;

import java.util.List;
import java.util.Map;

public record EmbeddingRequest(
        List<String> inputs,
        String modelRef,
        Map<String, Object> metadata
) {
    public EmbeddingRequest {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
