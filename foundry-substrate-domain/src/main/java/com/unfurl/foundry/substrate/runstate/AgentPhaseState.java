package com.unfurl.foundry.substrate.runstate;

import com.unfurl.foundry.substrate.model.Message;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Memento: records phase input, transcript, observations and outcome; genuine waits retain a typed suspended tool.
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
        Instant completedAt,
        ToolSuspension suspension
) {
    /** Snapshot constructor: preserves nonsuspended callers without fabricating a pending transaction. */
    public AgentPhaseState(String phaseId, AgentPhaseStatus status, Map<String, Object> input,
            List<Message> messages, List<ToolCall> toolCalls, Map<String, Object> output,
            String errorCode, String errorMessage, Instant startedAt, Instant completedAt) {
        this(phaseId, status, input, messages, toolCalls, output, errorCode, errorMessage, startedAt, completedAt, null);
    }
/**
 * Snapshot constructor: enforces wait/cancellation identity and deeply freezes suspended execution payloads.
 */
    public AgentPhaseState {
        input = input == null ? Map.of() : Map.copyOf(input);
        messages = messages == null ? List.of() : List.copyOf(messages);
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        output = output == null ? Map.of() : Map.copyOf(output);
        if (status == AgentPhaseStatus.WAITING && (suspension == null || completedAt != null
                || errorCode != null || errorMessage != null))
            throw new IllegalArgumentException("waiting phase requires a live suspended transaction, not failure");
        if (suspension != null) {
            if ((status != AgentPhaseStatus.WAITING && status != AgentPhaseStatus.CANCELLED)
                    || !phaseId.equals(suspension.phaseId()) || startedAt == null
                    || startedAt.isAfter(suspension.suspendedAt()))
                throw new IllegalArgumentException("phase suspension identity or lifecycle changed");
            input = ExecutionJsonSnapshot.freeze(input);
            messages = messages.stream().map(message -> new Message(message.role(), message.content(),
                    message.toolCallId(), ExecutionJsonSnapshot.freeze(message.metadata()))).toList();
            toolCalls = toolCalls.stream().map(call -> new ToolCall(call.callId(), call.toolName(),
                    ExecutionJsonSnapshot.freeze(call.arguments()), ExecutionJsonSnapshot.freeze(call.result()),
                    call.errorCode(), call.errorMessage(), call.startedAt(), call.completedAt())).toList();
            output = ExecutionJsonSnapshot.freeze(output);
        }
    }

    /** Snapshot Builder: freezes earlier completed phase payloads when the containing run becomes a suspended memento. */
    public AgentPhaseState frozenExecutionSnapshot() {
        var frozenMessages = messages.stream().map(message -> new Message(message.role(), message.content(),
                message.toolCallId(), ExecutionJsonSnapshot.freeze(message.metadata()))).toList();
        var frozenCalls = toolCalls.stream().map(call -> new ToolCall(call.callId(), call.toolName(),
                ExecutionJsonSnapshot.freeze(call.arguments()), ExecutionJsonSnapshot.freeze(call.result()),
                call.errorCode(), call.errorMessage(), call.startedAt(), call.completedAt())).toList();
        return new AgentPhaseState(phaseId, status, ExecutionJsonSnapshot.freeze(input), frozenMessages, frozenCalls,
                ExecutionJsonSnapshot.freeze(output), errorCode, errorMessage, startedAt, completedAt, suspension);
    }

/**
 * Implements the pending helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    public static AgentPhaseState pending(String phaseId) {
        return new AgentPhaseState(phaseId, AgentPhaseStatus.PENDING, Map.of(), List.of(), List.of(),
                Map.of(), null, null, null, null);
    }
}
