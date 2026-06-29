package com.unfurl.foundry.substrate.tool;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Neutral declaration of a tool an agent may call. {@code uses} is opaque; the
 * substrate never interprets it. The concrete handler is an executor resolved through
 * the tool registry.
 */
public record ToolDefinition(
        @NotBlank String name,
        @NotBlank String version,
        String description,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema,
        String uses,
        Map<String, Object> metadata
) {
/**
 * Constructs ToolDefinition with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public ToolDefinition {
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
        outputSchema = outputSchema == null ? Map.of() : Map.copyOf(outputSchema);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
