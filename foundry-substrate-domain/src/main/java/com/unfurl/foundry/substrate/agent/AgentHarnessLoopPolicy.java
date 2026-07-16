package com.unfurl.foundry.substrate.agent;

import java.util.Map;

/**
 * Value Object: bounds an embedded agent harness loop so repeated agent turns
 * remain deterministic and finite inside the substrate.
 */
public record AgentHarnessLoopPolicy(
        int maxTurns,
        long maxDurationMillis,
        Map<String, Object> metadata
) {
    /**
     * Factory method: supplies the conservative default policy for callers that
     * want one agent turn without an outer harness loop.
     */
    public static AgentHarnessLoopPolicy singleTurn() {
        return new AgentHarnessLoopPolicy(1, 0L, Map.of());
    }

    /**
     * Factory method: creates a turn-bounded policy while leaving wall-clock
     * deadlines to the host runtime.
     */
    public static AgentHarnessLoopPolicy boundedTurns(int maxTurns) {
        return new AgentHarnessLoopPolicy(maxTurns, 0L, Map.of());
    }

    /**
     * Constructs AgentHarnessLoopPolicy and preserves the invariant that every
     * harness loop has a finite positive turn budget.
     */
    public AgentHarnessLoopPolicy {
        if (maxTurns <= 0) {
            throw new IllegalArgumentException("maxTurns must be > 0");
        }
        if (maxDurationMillis < 0) {
            throw new IllegalArgumentException("maxDurationMillis must be >= 0");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
