package com.unfurl.foundry.substrate.events;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

/**
 * Neutral AI event. Mirrors the substrate {@code Event} envelope (carrying the same
 * {@code correlationId}/{@code integrityHash} metadata fields) but is keyed to agent
 * runs/phases and carries {@link AgentEventType}. Payloads are metadata-first: token
 * counts, tool names, model refs, phase ids — never full prompts/outputs/chunks by default.
 */
public record AgentEvent(
        String eventId,
        @NotNull Instant timestamp,
        String agentId,
        String runId,
        String phaseId,
        String tenantId,
        String userId,
        String correlationId,
        String integrityHash,
        @NotNull AgentEventType eventType,
        Map<String, Object> payload
) {
/**
 * Constructs AgentEvent with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public AgentEvent {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
