package com.unfurl.foundry.substrate.ports;

import java.util.Map;

public record ToolCallRequest(
        String callId,
        String toolName,
        Map<String, Object> arguments,
        Map<String, Object> metadata
) {
    public ToolCallRequest {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
