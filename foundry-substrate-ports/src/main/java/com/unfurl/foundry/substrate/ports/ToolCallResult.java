package com.unfurl.foundry.substrate.ports;

import java.util.Map;

/**
 * record for the Foundry AI substrate surface; documents the ToolCallResult contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public record ToolCallResult(
        boolean success,
        Map<String, Object> output,
        String errorCode,
        String errorMessage
) {
/**
 * Constructs ToolCallResult with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public ToolCallResult {
        output = output == null ? Map.of() : Map.copyOf(output);
    }

/**
 * Factory method: creates the success result while keeping caller-facing defaults and validation in one place.
 */
    public static ToolCallResult success(Map<String, Object> output) {
        return new ToolCallResult(true, output, null, null);
    }

/**
 * Factory method: creates the failure result while keeping caller-facing defaults and validation in one place.
 */
    public static ToolCallResult failure(String code, String message) {
        return new ToolCallResult(false, Map.of(), code, message);
    }
}
