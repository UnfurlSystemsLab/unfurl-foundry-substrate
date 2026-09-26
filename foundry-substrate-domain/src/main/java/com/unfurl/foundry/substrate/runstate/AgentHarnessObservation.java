package com.unfurl.foundry.substrate.runstate;

import com.unfurl.foundry.substrate.terminal.AgentTerminalEnvelope;

import java.time.Instant;
import java.util.Map;

/**
 * Value Object: records one harness turn's inner agent run and the decision
 * interpreted from that run's terminal output.
 */
public record AgentHarnessObservation(
        int turn,
        String agentRunId,
        AgentRunStatus agentStatus,
        Map<String, Object> output,
        String decisionKind,
        String message,
        Instant startedAt,
        Instant completedAt,
        AgentTerminalEnvelope terminalEnvelope
) {
    /**
     * Compatibility constructor: preserves the former observation signature during migration.
     */
    public AgentHarnessObservation(
            int turn,
            String agentRunId,
            AgentRunStatus agentStatus,
            Map<String, Object> output,
            String decisionKind,
            String message,
            Instant startedAt,
            Instant completedAt) {
        this(turn, agentRunId, agentStatus, output, decisionKind, message, startedAt, completedAt, null);
    }
    /**
     * Constructs AgentHarnessObservation with defensive output copies so
     * harness history remains stable after a turn completes.
     */
    public AgentHarnessObservation {
        if (turn <= 0) {
            throw new IllegalArgumentException("turn must be > 0");
        }
        output = output == null ? Map.of() : Map.copyOf(output);
    }
}
