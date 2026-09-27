package com.unfurl.foundry.substrate.context;

import java.util.List;
import java.util.Map;

/**
 * Value Object: declares the complete, reproducible policy used to select agent context.
 */
public record ContextPolicy(
        List<String> historyRefs,
        List<String> pinnedFactRefs,
        List<String> summaryRefs,
        boolean retainToolResults,
        boolean retainProvenance,
        int tokenAllocation,
        Map<String, Object> metadata
) {
    /** Canonical constructor: freezes policy values and requires a usable token allocation. */
    public ContextPolicy {
        historyRefs = historyRefs == null ? List.of() : List.copyOf(historyRefs);
        pinnedFactRefs = pinnedFactRefs == null ? List.of() : List.copyOf(pinnedFactRefs);
        summaryRefs = summaryRefs == null ? List.of() : List.copyOf(summaryRefs);
        if (tokenAllocation <= 0) {
            throw new IllegalArgumentException("tokenAllocation must be > 0");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
