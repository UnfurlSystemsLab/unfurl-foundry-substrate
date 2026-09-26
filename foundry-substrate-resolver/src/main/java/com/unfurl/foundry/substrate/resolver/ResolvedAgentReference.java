package com.unfurl.foundry.substrate.resolver;

import com.unfurl.foundry.substrate.agent.AgentHarnessLoopPolicy;
import com.unfurl.foundry.substrate.agent.BudgetPolicy;

import java.util.List;
import java.util.Map;

/** Value Object: immutable, provider-neutral resolved view of one pinned agentRef. */
public record ResolvedAgentReference(
        String reference,
        String agentId,
        String agentVersion,
        String definitionDigest,
        String inputSchemaRef,
        String terminalOutputSchemaRef,
        AgentHarnessLoopPolicy harnessPolicy,
        Map<String, List<String>> dependencyProvenance,
        BudgetPolicy budgetPolicy,
        List<String> permissionConstraints,
        Map<String, Object> governanceConstraints,
        Map<String, Object> dcpBindingIdentity,
        Map<String, Object> metadata
) {
    /** Preserves immutable collection boundaries for reproducible serialization and audit. */
    public ResolvedAgentReference {
        dependencyProvenance = dependencyProvenance == null ? Map.of() : Map.copyOf(dependencyProvenance);
        budgetPolicy = budgetPolicy == null ? BudgetPolicy.none() : budgetPolicy;
        permissionConstraints = permissionConstraints == null ? List.of() : List.copyOf(permissionConstraints);
        governanceConstraints = governanceConstraints == null ? Map.of() : Map.copyOf(governanceConstraints);
        dcpBindingIdentity = dcpBindingIdentity == null ? Map.of() : Map.copyOf(dcpBindingIdentity);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
