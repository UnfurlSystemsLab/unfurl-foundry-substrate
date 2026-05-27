package com.unfurl.foundry.substrate.runstate;

import java.time.Instant;
import java.util.Map;

/** Record of a tool call executed during a phase. */
public record ToolCall(
        String callId,
        String toolName,
        Map<String, Object> arguments,
        Map<String, Object> result,
        String errorCode,
        String errorMessage,
        Instant startedAt,
        Instant completedAt
) {
    public ToolCall {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        result = result == null ? Map.of() : Map.copyOf(result);
    }
}
