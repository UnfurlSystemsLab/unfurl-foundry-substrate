package com.unfurl.foundry.substrate.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ModelResponse(
        Message message,
        List<ModelToolCall> toolCalls,
        String finishReason,
        ModelUsage usage,
        Map<String, Object> metadata,
        String providerName,
        BigDecimal estimatedCostUsd
) {
    public ModelResponse(
            Message message,
            List<ModelToolCall> toolCalls,
            String finishReason,
            ModelUsage usage,
            Map<String, Object> metadata
    ) {
        this(message, toolCalls, finishReason, usage, metadata, providerNameFrom(metadata), estimatedCostUsdFrom(metadata));
    }

    public ModelResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        usage = usage == null ? ModelUsage.zero() : usage;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        estimatedCostUsd = estimatedCostUsd == null ? BigDecimal.ZERO : estimatedCostUsd;
        if (estimatedCostUsd.signum() < 0) {
            throw new IllegalArgumentException("estimatedCostUsd must be >= 0");
        }
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    private static String providerNameFrom(Map<String, Object> metadata) {
        Object value = metadata == null ? null : metadata.get("providerName");
        return value == null ? null : String.valueOf(value);
    }

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
