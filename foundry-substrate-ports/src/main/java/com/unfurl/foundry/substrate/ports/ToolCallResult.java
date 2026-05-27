package com.unfurl.foundry.substrate.ports;

import java.util.Map;

public record ToolCallResult(
        boolean success,
        Map<String, Object> output,
        String errorCode,
        String errorMessage
) {
    public ToolCallResult {
        output = output == null ? Map.of() : Map.copyOf(output);
    }

    public static ToolCallResult success(Map<String, Object> output) {
        return new ToolCallResult(true, output, null, null);
    }

    public static ToolCallResult failure(String code, String message) {
        return new ToolCallResult(false, Map.of(), code, message);
    }
}
