package com.unfurl.foundry.substrate.terminal;

import com.unfurl.foundry.substrate.failure.StructuredFailure;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Value Object: stable provider-neutral result of an agent run, including waits, handoff,
 * confidence, provenance, failure, metering, and extension metadata.
 */
public record AgentTerminalEnvelope(
        AgentTerminalStatus status,
        Map<String, Object> output,
        List<Object> questions,
        Map<String, Object> handoff,
        Map<String, Object> confidence,
        Map<String, Object> provenance,
        StructuredFailure error,
        Map<String, Object> metering,
        Map<String, Object> metadata
) {
    /**
     * Canonical constructor: validates status/error agreement and freezes all collection values.
     */
    public AgentTerminalEnvelope {
        status = Objects.requireNonNull(status, "terminal status is required");
        output = output == null ? Map.of() : Map.copyOf(output);
        questions = questions == null ? List.of() : List.copyOf(questions);
        handoff = handoff == null ? Map.of() : Map.copyOf(handoff);
        confidence = confidence == null ? Map.of() : Map.copyOf(confidence);
        provenance = provenance == null ? Map.of() : Map.copyOf(provenance);
        metering = metering == null ? Map.of() : Map.copyOf(metering);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if (status == AgentTerminalStatus.FAILED && error == null) {
            throw new IllegalArgumentException("FAILED terminal envelope requires error");
        }
        if (status != AgentTerminalStatus.FAILED && error != null) {
            throw new IllegalArgumentException("only FAILED terminal envelope may contain error");
        }
    }
}
