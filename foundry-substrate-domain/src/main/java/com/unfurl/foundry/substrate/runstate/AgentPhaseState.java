package com.unfurl.foundry.substrate.runstate;

import com.unfurl.foundry.substrate.model.Message;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * record for the Foundry AI substrate surface; documents the AgentPhaseState contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public record AgentPhaseState(
        String phaseId,
        AgentPhaseStatus status,
        Map<String, Object> input,
        List<Message> messages,
        List<ToolCall> toolCalls,
        Map<String, Object> output,
        String errorCode,
        String errorMessage,
        Instant startedAt,
        Instant completedAt
) {
/**
 * Constructs AgentPhaseState with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public AgentPhaseState {
        input = input == null ? Map.of() : Map.copyOf(input);
        messages = messages == null ? List.of() : List.copyOf(messages);
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        output = output == null ? Map.of() : Map.copyOf(output);
    }

/**
 * Implements the pending helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    public static AgentPhaseState pending(String phaseId) {
        return new AgentPhaseState(phaseId, AgentPhaseStatus.PENDING, Map.of(), List.of(), List.of(),
                Map.of(), null, null, null, null);
    }
}
