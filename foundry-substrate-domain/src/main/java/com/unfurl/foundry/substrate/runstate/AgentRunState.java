package com.unfurl.foundry.substrate.runstate;

import java.time.Instant;
import java.util.Map;

/**
 * Memento: holds one agent's graph execution, accounting and original input under its exact run identity.
 */
public record AgentRunState(
        String tenantId,
        String runId,
        String agentId,
        String agentVersion,
        AgentRunStatus status,
        Map<String, Object> agentInput,
        Map<String, AgentPhaseState> phases,
        CostAccounting cost,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
/**
 * Snapshot constructor: requires exactly one live phase for WAITING and freezes all continuation data at suspension.
 */
    public AgentRunState {
        agentInput = agentInput == null ? Map.of() : Map.copyOf(agentInput);
        phases = phases == null ? Map.of() : Map.copyOf(phases);
        long waiting = phases.values().stream().filter(phase -> phase.status() == AgentPhaseStatus.WAITING).count();
        if ((status == AgentRunStatus.WAITING && waiting != 1) || (status != AgentRunStatus.WAITING && waiting != 0))
            throw new IllegalArgumentException("waiting agent requires exactly one waiting phase");
        if (status == AgentRunStatus.WAITING) {
            agentInput = ExecutionJsonSnapshot.freeze(agentInput);
            Map<String, AgentPhaseState> frozen = new java.util.LinkedHashMap<>();
            phases.forEach((id, phase) -> frozen.put(id, phase.frozenExecutionSnapshot()));
            phases = Map.copyOf(frozen);
        }
        phases.forEach((id, phase) -> {
            if (phase.suspension() != null && (!id.equals(phase.phaseId())
                    || !runId.equals(phase.suspension().agentRunId())
                    || !java.util.Objects.equals(tenantId, phase.suspension().tenantId())
                    || (phase.status() == AgentPhaseStatus.CANCELLED && status != AgentRunStatus.CANCELLED)
                    || updatedAt == null || updatedAt.isBefore(phase.suspension().suspendedAt())))
                throw new IllegalArgumentException("agent tool suspension scope changed");
        });
    }
}
