package com.unfurl.foundry.substrate.runstate;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-run cost capture: accumulated token tallies plus attribution dimensions for a
 * reporting layer above to roll up by tenant, agent, model, or contract.
 *
 * <p>Capture only — never enforcement (that is the {@code CostGuardrail} port) and never
 * aggregation/persistence/billing (that is the reporting layer in {@code unfurl-foundry}).
 */
public record CostAccounting(
        long promptTokens,
        long completionTokens,
        Map<String, Long> tokensByModel,
        Map<String, Long> tokensByProvider,
        Map<String, Object> attribution,
        BigDecimal estimatedCostUsd
) {
/**
 * Constructs CostAccounting with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public CostAccounting(
            long promptTokens,
            long completionTokens,
            Map<String, Long> tokensByModel,
            Map<String, Long> tokensByProvider,
            Map<String, Object> attribution
    ) {
        this(promptTokens, completionTokens, tokensByModel, tokensByProvider, attribution, BigDecimal.ZERO);
    }

/**
 * Constructs CostAccounting with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public CostAccounting {
        tokensByModel = tokensByModel == null ? Map.of() : Map.copyOf(tokensByModel);
        tokensByProvider = tokensByProvider == null ? Map.of() : Map.copyOf(tokensByProvider);
        attribution = attribution == null ? Map.of() : Map.copyOf(attribution);
        estimatedCostUsd = estimatedCostUsd == null ? BigDecimal.ZERO : estimatedCostUsd;
        if (estimatedCostUsd.signum() < 0) {
            throw new IllegalArgumentException("estimatedCostUsd must be >= 0");
        }
    }

/**
 * Implements the totalTokens helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    public long totalTokens() {
        return promptTokens + completionTokens;
    }

/**
 * Factory method: creates the empty result while keeping caller-facing defaults and validation in one place.
 */
    public static CostAccounting empty(Map<String, Object> attribution) {
        return new CostAccounting(0, 0, Map.of(), Map.of(), attribution);
    }

    /** Returns a new accounting with the given usage folded in, attributed to model/provider. */
/**
 * Implements the add helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    public CostAccounting add(long prompt, long completion, String modelRef, String providerName) {
        return add(prompt, completion, modelRef, providerName, BigDecimal.ZERO);
    }

    /** Returns a new accounting with the given usage and estimated cost folded in. */
/**
 * Implements the add helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    public CostAccounting add(long prompt, long completion, String modelRef, String providerName, BigDecimal estimatedCostDeltaUsd) {
        Map<String, Long> byModel = new LinkedHashMap<>(tokensByModel);
        Map<String, Long> byProvider = new LinkedHashMap<>(tokensByProvider);
        long delta = prompt + completion;
        BigDecimal costDelta = estimatedCostDeltaUsd == null ? BigDecimal.ZERO : estimatedCostDeltaUsd;
        if (costDelta.signum() < 0) {
            throw new IllegalArgumentException("estimatedCostDeltaUsd must be >= 0");
        }
        if (modelRef != null) {
            byModel.merge(modelRef, delta, Long::sum);
        }
        if (providerName != null) {
            byProvider.merge(providerName, delta, Long::sum);
        }
        return new CostAccounting(
                promptTokens + prompt,
                completionTokens + completion,
                byModel,
                byProvider,
                attribution,
                estimatedCostUsd.add(costDelta)
        );
    }
}
