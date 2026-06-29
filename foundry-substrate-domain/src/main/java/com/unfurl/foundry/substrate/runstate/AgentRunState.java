package com.unfurl.foundry.substrate.runstate;

import java.time.Instant;
import java.util.Map;

/**
 * record for the Foundry AI substrate surface; documents the AgentRunState contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
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
 * Constructs AgentRunState with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public AgentRunState {
        agentInput = agentInput == null ? Map.of() : Map.copyOf(agentInput);
        phases = phases == null ? Map.of() : Map.copyOf(phases);
    }
}
