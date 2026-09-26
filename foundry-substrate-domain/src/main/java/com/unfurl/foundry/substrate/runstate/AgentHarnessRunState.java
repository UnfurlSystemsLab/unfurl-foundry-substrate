package com.unfurl.foundry.substrate.runstate;

import com.unfurl.foundry.substrate.terminal.AgentTerminalEnvelope;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Aggregate Snapshot: captures the state of a bounded agent harness run without
 * prescribing persistence. Foundry can checkpoint this record; embedded hosts
 * can keep it in memory.
 */
public record AgentHarnessRunState(
        String tenantId,
        String runId,
        String harnessId,
        String harnessVersion,
        AgentHarnessStatus status,
        int turn,
        Map<String, Object> originalInput,
        Map<String, Object> latestInput,
        List<AgentHarnessObservation> observations,
        Map<String, Object> output,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt,
        AgentTerminalEnvelope terminalEnvelope
) {
    /**
     * Compatibility constructor: preserves the former harness snapshot signature during migration.
     */
    public AgentHarnessRunState(
            String tenantId,
            String runId,
            String harnessId,
            String harnessVersion,
            AgentHarnessStatus status,
            int turn,
            Map<String, Object> originalInput,
            Map<String, Object> latestInput,
            List<AgentHarnessObservation> observations,
            Map<String, Object> output,
            String errorCode,
            String errorMessage,
            Instant createdAt,
            Instant updatedAt) {
        this(tenantId, runId, harnessId, harnessVersion, status, turn, originalInput, latestInput,
                observations, output, errorCode, errorMessage, createdAt, updatedAt, null);
    }
    /**
     * Constructs AgentHarnessRunState with defensive collection copies so
     * snapshots are safe to retain, serialize, or checkpoint.
     */
    public AgentHarnessRunState {
        originalInput = originalInput == null ? Map.of() : Map.copyOf(originalInput);
        latestInput = latestInput == null ? Map.of() : Map.copyOf(latestInput);
        observations = observations == null ? List.of() : List.copyOf(observations);
        output = output == null ? Map.of() : Map.copyOf(output);
    }
}
