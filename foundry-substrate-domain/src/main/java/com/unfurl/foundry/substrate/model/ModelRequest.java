package com.unfurl.foundry.substrate.model;

import java.util.List;
import java.util.Map;

/**
 * Neutral model-invocation request. Contains no provider-specific fields; adapters map
 * to and from concrete SDK types outside the substrate.
 */
public record ModelRequest(
        List<Message> messages,
        String modelRef,
        Map<String, Object> parameters,
        List<Map<String, Object>> toolSchemas,
        Map<String, Object> metadata
) {
    public ModelRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        toolSchemas = toolSchemas == null ? List.of() : List.copyOf(toolSchemas);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
