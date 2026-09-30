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
/**
 * Constructs BudgetPolicy with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
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

/**
 * Factory method: creates the none result while keeping caller-facing defaults and validation in one place.
 */
    public static BudgetPolicy none() {
        return new BudgetPolicy(null, null, null, null, null, Map.of());
    }

    /** Composition Strategy: selects lower non-null ceilings and clamps default USD to a stricter maximum. */
    public static BudgetPolicy lowerOf(BudgetPolicy outer, BudgetPolicy inner) {
        BudgetPolicy left = outer == null ? none() : outer;
        BudgetPolicy right = inner == null ? none() : inner;
        Map<String, Object> metadata = new java.util.LinkedHashMap<>(right.metadata());
        metadata.putAll(left.metadata());
        BigDecimal maximum = min(left.maxBudgetUsd(), right.maxBudgetUsd());
        BigDecimal defaultUsd = min(left.defaultBudgetUsd(), right.defaultBudgetUsd());
        if (defaultUsd != null && maximum != null) defaultUsd = defaultUsd.min(maximum);
        return new BudgetPolicy(defaultUsd, maximum, min(left.maxPromptTokens(), right.maxPromptTokens()),
                min(left.maxCompletionTokens(), right.maxCompletionTokens()), min(left.maxTotalTokens(), right.maxTotalTokens()), metadata);
    }

    /** Numeric helper: null means no ceiling, while zero remains an explicit exhausted ceiling. */
    private static Long min(Long left, Long right) {
        if (left == null) return right;
        if (right == null) return left;
        return Math.min(left, right);
    }

    /** Decimal helper: null means no ceiling, avoiding implicit unlimited-to-zero conversions. */
    private static BigDecimal min(BigDecimal left, BigDecimal right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.min(right);
    }

/**
 * Performs the hasUsdCeiling operation for this component, translating validated inputs into the domain result expected by callers.
 */
    public boolean hasUsdCeiling() {
        return defaultBudgetUsd != null || maxBudgetUsd != null;
    }

/**
 * Performs the hasTokenCeiling operation for this component, translating validated inputs into the domain result expected by callers.
 */
    public boolean hasTokenCeiling() {
        return maxPromptTokens != null || maxCompletionTokens != null || maxTotalTokens != null;
    }

/**
 * Implements the requireNonNegative helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private static void requireNonNegative(Long value, String field) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(field + " must be >= 0");
        }
    }
}
