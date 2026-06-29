package com.unfurl.foundry.substrate.skill;

import com.unfurl.foundry.substrate.agent.BudgetPolicy;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * A named, versioned bundle of agent refs and governance that expands before runtime.
 */
public record SkillDefinition(
        @NotBlank String name,
        @NotBlank String version,
        String description,
        List<String> toolRefs,
        String promptFragmentRef,
        List<String> ragSourceRefs,
        String defaultModelRef,
        BudgetPolicy budgetPolicy,
        List<String> permissionScope,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema,
        Map<String, Object> metadata
) {
/**
 * Constructs SkillDefinition with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public SkillDefinition {
        toolRefs = toolRefs == null ? List.of() : List.copyOf(toolRefs);
        ragSourceRefs = ragSourceRefs == null ? List.of() : List.copyOf(ragSourceRefs);
        budgetPolicy = budgetPolicy == null ? BudgetPolicy.none() : budgetPolicy;
        permissionScope = permissionScope == null ? List.of() : List.copyOf(permissionScope);
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
        outputSchema = outputSchema == null ? Map.of() : Map.copyOf(outputSchema);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
