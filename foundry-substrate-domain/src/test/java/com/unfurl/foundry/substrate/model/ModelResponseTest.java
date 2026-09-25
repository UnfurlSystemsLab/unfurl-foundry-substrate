package com.unfurl.foundry.substrate.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies compatibility and invariants for the provider-neutral model-turn outcome contract. */
class ModelResponseTest {

    /** Legacy Factory compatibility: known completion strings still create completed responses. */
    @Test
    void derivesCompletedOutcomeFromLegacyStopReason() {
        ModelResponse response = new ModelResponse(
                Message.assistant("done"), List.of(), "end_turn", ModelUsage.zero(), Map.of());

        assertThat(response.outcome()).isEqualTo(ModelTurnOutcome.COMPLETED);
    }

    /** Fail-closed invariant: unknown non-empty provider reasons are never treated as completion. */
    @Test
    void unknownLegacyReasonFailsClosed() {
        ModelResponse response = new ModelResponse(
                Message.assistant(""), List.of(), "new_provider_reason", ModelUsage.zero(), Map.of());

        assertThat(response.outcome()).isEqualTo(ModelTurnOutcome.PROVIDER_ERROR);
    }

    /** Tool-loop invariant: native tool calls always derive the tool-requested outcome. */
    @Test
    void toolCallsTakePrecedenceOverLegacyReason() {
        ModelResponse response = new ModelResponse(
                Message.assistant(""), List.of(new ModelToolCall("call-1", "lookup", Map.of())),
                "end_turn", ModelUsage.zero(), Map.of());

        assertThat(response.outcome()).isEqualTo(ModelTurnOutcome.TOOL_REQUESTED);
    }

    /** Constructor invariant: an explicit tool-requested outcome must carry an executable call. */
    @Test
    void rejectsToolRequestedWithoutToolCall() {
        assertThatThrownBy(() -> new ModelResponse(
                Message.assistant(""), List.of(), "tool_use", ModelTurnOutcome.TOOL_REQUESTED,
                ModelUsage.zero(), Map.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires at least one tool call");
    }
}
