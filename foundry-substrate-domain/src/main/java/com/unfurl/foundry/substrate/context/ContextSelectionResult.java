package com.unfurl.foundry.substrate.context;

import java.util.List;
import java.util.Map;

/**
 * Value Object: deterministic selected context and its attribution/accounting data.
 */
public record ContextSelectionResult(
        Map<String, Object> context,
        List<ContextResource> resources,
        Map<String, Object> provenance,
        int estimatedTokens,
        List<Map<String, Object>> unresolvedWaits,
        Map<String, Object> metadata
) {
    /** Canonical constructor: validates accounting and freezes the selection result. */
    public ContextSelectionResult {
        context = context == null ? Map.of() : Map.copyOf(context);
        resources = resources == null ? List.of() : List.copyOf(resources);
        provenance = provenance == null ? Map.of() : Map.copyOf(provenance);
        if (estimatedTokens < 0) throw new IllegalArgumentException("estimatedTokens must be >= 0");
        unresolvedWaits = unresolvedWaits == null ? List.of() : List.copyOf(unresolvedWaits);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
