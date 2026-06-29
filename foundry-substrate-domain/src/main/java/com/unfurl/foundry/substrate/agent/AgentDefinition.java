package com.unfurl.foundry.substrate.agent;

import com.unfurl.substrate.domain.EdgeDefinition;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * A multi-phase agent expressed as a static DAG of phases joined by conditional edges.
 *
 * <p>Topology is static; runtime data affects routing only through conditional edges
 * between phases. This deliberately reuses the substrate's static-DAG model
 * ({@link EdgeDefinition} + condition definitions) so a host's durable engine can drive
 * agent phases as ordinary DAG nodes.
 */
public record AgentDefinition(
        @NotBlank String id,
        @NotBlank String version,
        Map<String, Object> metadata,
        List<AgentPhase> phases,
        List<EdgeDefinition> edges,
        Map<String, Object> inputSchema,
        String defaultModelRef,
        List<String> toolRefs,
        BudgetPolicy budgetPolicy,
        List<String> skillRefs
) {
/**
 * Constructs AgentDefinition with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public AgentDefinition(
            String id,
            String version,
            Map<String, Object> metadata,
            List<AgentPhase> phases,
            List<EdgeDefinition> edges,
            Map<String, Object> inputSchema,
            String defaultModelRef,
            List<String> toolRefs,
            BudgetPolicy budgetPolicy
    ) {
        this(id, version, metadata, phases, edges, inputSchema, defaultModelRef, toolRefs, budgetPolicy, List.of());
    }

/**
 * Constructs AgentDefinition with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public AgentDefinition(
            String id,
            String version,
            Map<String, Object> metadata,
            List<AgentPhase> phases,
            List<EdgeDefinition> edges,
            Map<String, Object> inputSchema,
            String defaultModelRef,
            List<String> toolRefs
    ) {
        this(id, version, metadata, phases, edges, inputSchema, defaultModelRef, toolRefs, BudgetPolicy.none(), List.of());
    }

/**
 * Constructs AgentDefinition with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public AgentDefinition {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        phases = phases == null ? List.of() : List.copyOf(phases);
        edges = edges == null ? List.of() : List.copyOf(edges);
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
        toolRefs = toolRefs == null ? List.of() : List.copyOf(toolRefs);
        budgetPolicy = budgetPolicy == null ? BudgetPolicy.none() : budgetPolicy;
        skillRefs = skillRefs == null ? List.of() : List.copyOf(skillRefs);
    }
}
