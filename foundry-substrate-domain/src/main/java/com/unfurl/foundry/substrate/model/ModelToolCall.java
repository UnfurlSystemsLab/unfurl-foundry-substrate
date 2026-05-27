package com.unfurl.foundry.substrate.model;

import java.util.Map;

/** A tool call requested by the model within a {@link ModelResponse}. */
public record ModelToolCall(
        String id,
        String toolName,
        Map<String, Object> arguments
) {
    public ModelToolCall {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
