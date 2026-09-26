package com.unfurl.foundry.substrate.terminal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the Strategy that migrates legacy agent terminal maps to the canonical envelope. */
class TerminalEnvelopeNormalizerTest {
    private final TerminalEnvelopeNormalizer normalizer = new TerminalEnvelopeNormalizer();

    /** Verifies every supported legacy terminal signal maps to a provider-neutral status. */
    @Test
    void normalizesSupportedLegacyTerminalSignals() {
        assertThat(normalizer.normalize(Map.of("kind", "complete")).status())
                .isEqualTo(AgentTerminalStatus.COMPLETED);
        assertThat(normalizer.normalize(Map.of("questions", List.of("Which tenant?"))).status())
                .isEqualTo(AgentTerminalStatus.WAITING_FOR_USER);
        assertThat(normalizer.normalize(Map.of("kind", "approval", "questions", List.of("Approve?"))).status())
                .isEqualTo(AgentTerminalStatus.WAITING_FOR_APPROVAL);
        assertThat(normalizer.normalize(Map.of("unmet", List.of("tool.call"))).status())
                .isEqualTo(AgentTerminalStatus.GAP);
        assertThat(normalizer.normalize(Map.of("kind", "escalate")).status())
                .isEqualTo(AgentTerminalStatus.ESCALATED);
        assertThat(normalizer.normalize(Map.of("kind", "cancelled")).status())
                .isEqualTo(AgentTerminalStatus.CANCELLED);
    }

    /** Verifies malformed clarification fails closed with a structured validation error. */
    @Test
    void rejectsClarificationWithoutQuestions() {
        AgentTerminalEnvelope envelope = normalizer.normalize(Map.of("kind", "clarify"));

        assertThat(envelope.status()).isEqualTo(AgentTerminalStatus.FAILED);
        assertThat(envelope.error().code()).isEqualTo("AGENT_OUTPUT_INVALID");
    }
}
