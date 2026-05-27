package com.unfurl.foundry.substrate.runstate;

import com.unfurl.foundry.substrate.model.Message;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
    public AgentPhaseState {
        input = input == null ? Map.of() : Map.copyOf(input);
        messages = messages == null ? List.of() : List.copyOf(messages);
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        output = output == null ? Map.of() : Map.copyOf(output);
    }

    public static AgentPhaseState pending(String phaseId) {
        return new AgentPhaseState(phaseId, AgentPhaseStatus.PENDING, Map.of(), List.of(), List.of(),
                Map.of(), null, null, null, null);
    }
}
