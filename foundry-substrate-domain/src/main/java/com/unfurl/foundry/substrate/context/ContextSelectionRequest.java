package com.unfurl.foundry.substrate.context;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Value Object: explicit inputs from which a ContextSelector may assemble model context.
 */
public record ContextSelectionRequest(
        Map<String, Object> input,
        Map<String, Object> history,
        Map<String, Object> pinnedFacts,
        Map<String, Object> summaries,
        Map<String, Object> toolResults,
        Map<String, Object> provenance,
        List<String> resourceRefs,
        List<Map<String, Object>> unresolvedWaits,
        ContextPolicy policy,
        Map<String, Object> metadata
) {
    /** Canonical constructor: freezes all explicitly supplied context candidates. */
    public ContextSelectionRequest {
        input = copy(input); history = copy(history); pinnedFacts = copy(pinnedFacts);
        summaries = copy(summaries); toolResults = copy(toolResults); provenance = copy(provenance);
        resourceRefs = resourceRefs == null ? List.of() : List.copyOf(resourceRefs);
        unresolvedWaits = unresolvedWaits == null ? List.of() : List.copyOf(unresolvedWaits);
        policy = Objects.requireNonNull(policy, "context policy is required");
        metadata = copy(metadata);
    }

    /** Helper: converts nullable maps to immutable values without introducing implicit context. */
    private static Map<String, Object> copy(Map<String, Object> value) {
        return value == null ? Map.of() : Map.copyOf(value);
    }
}
