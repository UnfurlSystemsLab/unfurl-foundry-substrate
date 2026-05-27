package com.unfurl.foundry.substrate.model;

import java.util.List;
import java.util.Map;

public record ModelResponse(
        Message message,
        List<ModelToolCall> toolCalls,
        String finishReason,
        ModelUsage usage,
        Map<String, Object> metadata
) {
    public ModelResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        usage = usage == null ? ModelUsage.zero() : usage;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
