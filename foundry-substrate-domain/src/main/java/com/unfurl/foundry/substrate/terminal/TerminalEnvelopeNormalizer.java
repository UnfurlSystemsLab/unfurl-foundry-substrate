package com.unfurl.foundry.substrate.terminal;

import com.unfurl.foundry.substrate.failure.FailureCategory;
import com.unfurl.foundry.substrate.failure.StructuredFailure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Strategy: migrates legacy {@code kind}/{@code questions}/{@code unmet} output into the canonical
 * terminal envelope while failing malformed wait/failure shapes closed.
 */
public final class TerminalEnvelopeNormalizer {
    /**
     * Normalizes one successful agent output and retains the legacy map as canonical output.
     */
    public AgentTerminalEnvelope normalize(Map<String, Object> rawOutput) {
        Map<String, Object> output = rawOutput == null ? Map.of() : Map.copyOf(rawOutput);
        String kind = text(output.get("kind")).toLowerCase(Locale.ROOT);
        List<Object> questions = list(output.get("questions"));

        if ((kind.equals("clarify") || kind.equals("waiting_for_user")) && questions.isEmpty()) {
            return invalid("clarification output requires non-empty questions", output);
        }
        if (kind.equals("approval") || kind.equals("waiting_for_approval")) {
            return envelope(AgentTerminalStatus.WAITING_FOR_APPROVAL, output, questions, null);
        }
        if (kind.equals("clarify") || kind.equals("waiting_for_user") || !questions.isEmpty()) {
            return envelope(AgentTerminalStatus.WAITING_FOR_USER, output, questions, null);
        }
        if (kind.equals("gap") || nonEmpty(output.get("unmet"))) {
            return envelope(AgentTerminalStatus.GAP, output, questions, null);
        }
        if (kind.equals("escalate") || kind.equals("escalated")) {
            return envelope(AgentTerminalStatus.ESCALATED, output, questions, null);
        }
        if (kind.equals("cancelled") || kind.equals("canceled")) {
            return envelope(AgentTerminalStatus.CANCELLED, output, questions, null);
        }
        if (kind.equals("failed") || kind.equals("error")) {
            return invalid(textOr(output.get("message"), "Agent reported failure"), output);
        }
        return envelope(AgentTerminalStatus.COMPLETED, output, questions, null);
    }

    /**
     * Creates a canonical failed envelope from an expected sanitized runtime failure.
     */
    public AgentTerminalEnvelope failure(String code, String message, Map<String, Object> partialOutput) {
        StructuredFailure failure = new StructuredFailure(
                code == null || code.isBlank() ? "AGENT_FAILED" : code,
                FailureCategory.INTERNAL,
                false,
                null,
                message,
                partialOutput,
                Map.of(),
                Map.of());
        return envelope(AgentTerminalStatus.FAILED,
                partialOutput == null ? Map.of() : partialOutput, List.of(), failure);
    }

    /**
     * Factory: assembles common optional fields from legacy output while keeping reserved fields stable.
     */
    private AgentTerminalEnvelope envelope(
            AgentTerminalStatus status,
            Map<String, Object> output,
            List<Object> questions,
            StructuredFailure failure) {
        return new AgentTerminalEnvelope(
                status,
                output,
                questions,
                map(output.get("handoff")),
                map(output.get("confidence")),
                map(output.get("provenance")),
                failure,
                map(output.get("metering")),
                Map.of("legacyKind", text(output.get("kind"))));
    }

    /**
     * Fail-closed adapter: represents malformed legacy terminal output as AGENT_OUTPUT_INVALID.
     */
    private AgentTerminalEnvelope invalid(String message, Map<String, Object> output) {
        StructuredFailure failure = new StructuredFailure(
                "AGENT_OUTPUT_INVALID", FailureCategory.VALIDATION, false, null,
                message, output, Map.of(), Map.of());
        return envelope(AgentTerminalStatus.FAILED, output, List.of(), failure);
    }

    /**
     * Collection adapter: preserves heterogeneous legacy question shapes as immutable values.
     */
    private List<Object> list(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        return List.copyOf(new ArrayList<>(collection));
    }

    /**
     * Map adapter: converts arbitrary keys to strings for stable JSON/YAML serialization.
     */
    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        source.forEach((key, item) -> normalized.put(String.valueOf(key), item));
        return Map.copyOf(normalized);
    }

    /**
     * Predicate: detects non-empty legacy collection signals.
     */
    private boolean nonEmpty(Object value) {
        return value instanceof Collection<?> collection && !collection.isEmpty();
    }

    /**
     * Text adapter: normalizes nullable scalar values.
     */
    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /**
     * Text selector: uses a stable fallback when legacy message text is absent.
     */
    private String textOr(Object value, String fallback) {
        String text = text(value);
        return text.isBlank() ? fallback : text;
    }
}
