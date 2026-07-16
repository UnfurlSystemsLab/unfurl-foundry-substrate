package com.unfurl.foundry.substrate.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Aggregate Root: describes a bounded harness around one agent definition.
 * The harness owns repeated turns; the wrapped agent owns phase topology and
 * tool/model/RAG execution within each turn.
 */
public record AgentHarnessDefinition(
        @NotBlank String id,
        @NotBlank String version,
        Map<String, Object> metadata,
        @NotNull AgentDefinition agent,
        AgentHarnessLoopPolicy loopPolicy
) {
    /**
     * Constructs AgentHarnessDefinition with defensive copies and the default
     * single-turn loop policy when a host does not request a broader harness.
     */
    public AgentHarnessDefinition {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        loopPolicy = loopPolicy == null ? AgentHarnessLoopPolicy.singleTurn() : loopPolicy;
    }
}
