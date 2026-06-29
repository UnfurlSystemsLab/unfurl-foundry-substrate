package com.unfurl.foundry.substrate.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * record for the Foundry AI substrate surface; documents the ModelResponse contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public record ModelResponse(
        Message message,
        List<ModelToolCall> toolCalls,
        String finishReason,
        ModelUsage usage,
        Map<String, Object> metadata,
        String providerName,
        BigDecimal estimatedCostUsd
) {
/**
 * Constructs ModelResponse with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public ModelResponse(
            Message message,
            List<ModelToolCall> toolCalls,
            String finishReason,
            ModelUsage usage,
            Map<String, Object> metadata
    ) {
        this(message, toolCalls, finishReason, usage, metadata, providerNameFrom(metadata), estimatedCostUsdFrom(metadata));
    }

/**
 * Constructs ModelResponse with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public ModelResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        usage = usage == null ? ModelUsage.zero() : usage;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        estimatedCostUsd = estimatedCostUsd == null ? BigDecimal.ZERO : estimatedCostUsd;
        if (estimatedCostUsd.signum() < 0) {
            throw new IllegalArgumentException("estimatedCostUsd must be >= 0");
        }
    }

/**
 * Performs the hasToolCalls operation for this component, translating validated inputs into the domain result expected by callers.
 */
    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

/**
 * Implements the providerNameFrom helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private static String providerNameFrom(Map<String, Object> metadata) {
        Object value = metadata == null ? null : metadata.get("providerName");
        return value == null ? null : String.valueOf(value);
    }

/**
 * Implements the estimatedCostUsdFrom helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private static BigDecimal estimatedCostUsdFrom(Map<String, Object> metadata) {
        if (metadata == null) {
            return BigDecimal.ZERO;
        }
        Object value = metadata.getOrDefault("estimatedCostUsd", metadata.get("costUsd"));
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String string && !string.isBlank()) {
            return new BigDecimal(string);
        }
        return BigDecimal.ZERO;
    }
}
