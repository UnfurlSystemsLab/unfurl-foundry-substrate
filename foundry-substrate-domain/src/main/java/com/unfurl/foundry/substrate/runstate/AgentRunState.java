package com.unfurl.foundry.substrate.runstate;

import java.time.Instant;
import java.util.Map;

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
    public AgentRunState {
        agentInput = agentInput == null ? Map.of() : Map.copyOf(agentInput);
        phases = phases == null ? Map.of() : Map.copyOf(phases);
    }
}
