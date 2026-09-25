package com.unfurl.foundry.substrate.model;

import java.util.Locale;

/**
 * Value Object: provider-neutral reason that a model turn returned control to the agent runtime.
 * Adapters translate native finish reasons into this vocabulary so engine control flow never depends
 * on provider strings. Unknown non-empty legacy reasons fail closed as {@link #PROVIDER_ERROR}.
 */
public enum ModelTurnOutcome {
    TOOL_REQUESTED,
    COMPLETED,
    MAX_OUTPUT_REACHED,
    CONTENT_FILTERED,
    PROVIDER_ERROR;

    /**
     * Compatibility Factory: maps the legacy finish-reason field while giving actual tool calls
     * precedence. A blank legacy reason is treated as completion for old providers that omitted it.
     */
    public static ModelTurnOutcome fromLegacy(String finishReason, boolean hasToolCalls) {
        if (hasToolCalls) {
            return TOOL_REQUESTED;
        }
        if (finishReason == null || finishReason.isBlank()) {
            return COMPLETED;
        }
        String normalized = finishReason.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "tool_use", "tool_calls", "function_call", "function_calls" -> TOOL_REQUESTED;
            case "stop", "end_turn", "stop_sequence", "complete", "completed", "finish_reason_stop" -> COMPLETED;
            case "length", "max_tokens", "max_output_tokens", "max_output_reached" -> MAX_OUTPUT_REACHED;
            case "content_filter", "content_filtered", "safety", "blocked", "blocklist", "prohibited_content",
                    "spii", "recitation" -> CONTENT_FILTERED;
            default -> PROVIDER_ERROR;
        };
    }
}
