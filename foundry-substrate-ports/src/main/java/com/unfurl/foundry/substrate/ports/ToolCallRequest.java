package com.unfurl.foundry.substrate.ports;

import java.util.Map;

/**
 * record for the Foundry AI substrate surface; documents the ToolCallRequest contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public record ToolCallRequest(
        String callId,
        String toolName,
        Map<String, Object> arguments,
        Map<String, Object> metadata
) {
/**
 * Constructs ToolCallRequest with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public ToolCallRequest {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
