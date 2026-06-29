package com.unfurl.foundry.substrate.model;

import java.util.Map;

/** A tool call requested by the model within a {@link ModelResponse}. */
/**
 * record for the Foundry AI substrate surface; documents the ModelToolCall contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public record ModelToolCall(
        String id,
        String toolName,
        Map<String, Object> arguments
) {
/**
 * Constructs ModelToolCall with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public ModelToolCall {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
