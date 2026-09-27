package com.unfurl.foundry.substrate.delegation;

import com.unfurl.foundry.substrate.agent.BudgetPolicy;
import com.unfurl.foundry.substrate.failure.StructuredFailure;
import com.unfurl.foundry.substrate.runstate.CostAccounting;
import com.unfurl.foundry.substrate.terminal.AgentTerminalEnvelope;
import java.util.List;
import java.util.Map;

/**
 * Value Object: lossless child outcome preserving success, failure, partial output, and authority.
 */
public record AgentDelegationResult(
        AgentTerminalEnvelope terminal,
        StructuredFailure failure,
        BudgetPolicy effectiveBudget,
        List<String> effectivePermissions,
        CostAccounting cost,
        Map<String, Object> provenance,
        Map<String, Object> metadata
) {
    /** Canonical constructor: enforces an exclusive terminal-or-failure result. */
    public AgentDelegationResult {
        if ((terminal == null) == (failure == null)) {
            throw new IllegalArgumentException("exactly one of terminal or failure is required");
        }
        effectiveBudget = effectiveBudget == null ? BudgetPolicy.none() : effectiveBudget;
        effectivePermissions = effectivePermissions == null ? List.of() : List.copyOf(effectivePermissions);
        provenance = provenance == null ? Map.of() : Map.copyOf(provenance);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** Query: reports success without conflating an empty successful output with failure. */
    public boolean success() { return terminal != null; }
}
