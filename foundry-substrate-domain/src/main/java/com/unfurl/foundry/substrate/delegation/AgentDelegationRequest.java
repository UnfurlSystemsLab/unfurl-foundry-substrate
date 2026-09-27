package com.unfurl.foundry.substrate.delegation;

import com.unfurl.foundry.substrate.agent.BudgetPolicy;
import java.util.List;
import java.util.Map;

/**
 * Value Object: least-authority child-agent invocation with explicit projected context.
 */
public record AgentDelegationRequest(
        String agentRef,
        String objective,
        Map<String, Object> contextProjection,
        List<String> sourceRefs,
        String expectedOutputSchemaRef,
        BudgetPolicy budgetEnvelope,
        List<String> permissionScope,
        Map<String, Object> metadata
) {
    /** Canonical constructor: requires a pinned reference and freezes all boundary values. */
    public AgentDelegationRequest {
        if (agentRef == null || !agentRef.matches("[^@\\s]+@[^@\\s]+")) {
            throw new IllegalArgumentException("agentRef must be pinned as id@version");
        }
        if (objective == null || objective.isBlank()) throw new IllegalArgumentException("objective is required");
        contextProjection = contextProjection == null ? Map.of() : Map.copyOf(contextProjection);
        sourceRefs = sourceRefs == null ? List.of() : List.copyOf(sourceRefs);
        budgetEnvelope = budgetEnvelope == null ? BudgetPolicy.none() : budgetEnvelope;
        permissionScope = permissionScope == null ? List.of() : List.copyOf(permissionScope);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
