package com.unfurl.foundry.substrate.runstate;

import java.time.Instant;
import java.util.Map;

/**
 * record for the Foundry AI substrate surface; documents the of contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
/** Record of a tool call executed during a phase. */
/**
 * record for the Foundry AI substrate surface; documents the ToolCall contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
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
/**
 * Constructs ToolCall with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public ToolCall {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        result = result == null ? Map.of() : Map.copyOf(result);
    }
}
