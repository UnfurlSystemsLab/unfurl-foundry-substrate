package com.unfurl.foundry.substrate.agent;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Agent-owned execution ceiling carried with a resolved agentRef.
 *
 * <p>USD fields are policy ceilings only. Concrete rate tables, quota state,
 * rollups, dashboards, and billing exports live above the substrate.
 */
public record BudgetPolicy(
        BigDecimal defaultBudgetUsd,
        BigDecimal maxBudgetUsd,
        Long maxPromptTokens,
        Long maxCompletionTokens,
        Long maxTotalTokens,
        Map<String, Object> metadata
) {
    public BudgetPolicy {
        if (defaultBudgetUsd != null && defaultBudgetUsd.signum() < 0) {
            throw new IllegalArgumentException("defaultBudgetUsd must be >= 0");
        }
        if (maxBudgetUsd != null && maxBudgetUsd.signum() < 0) {
            throw new IllegalArgumentException("maxBudgetUsd must be >= 0");
        }
        if (defaultBudgetUsd != null && maxBudgetUsd != null && defaultBudgetUsd.compareTo(maxBudgetUsd) > 0) {
            throw new IllegalArgumentException("defaultBudgetUsd must be <= maxBudgetUsd");
        }
        requireNonNegative(maxPromptTokens, "maxPromptTokens");
        requireNonNegative(maxCompletionTokens, "maxCompletionTokens");
        requireNonNegative(maxTotalTokens, "maxTotalTokens");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static BudgetPolicy none() {
        return new BudgetPolicy(null, null, null, null, null, Map.of());
    }

    public boolean hasUsdCeiling() {
        return defaultBudgetUsd != null || maxBudgetUsd != null;
    }

    public boolean hasTokenCeiling() {
        return maxPromptTokens != null || maxCompletionTokens != null || maxTotalTokens != null;
    }

    private static void requireNonNegative(Long value, String field) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(field + " must be >= 0");
        }
    }
}
