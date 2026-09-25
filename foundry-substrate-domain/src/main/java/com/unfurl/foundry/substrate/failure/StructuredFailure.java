package com.unfurl.foundry.substrate.failure;

import java.util.Map;
import java.util.Objects;

/**
 * Value Object: carries a sanitized, provider-neutral failure with explicit retry policy input,
 * optional partial output, diagnostic details, and attribution provenance.
 */
public record StructuredFailure(
        String code,
        FailureCategory category,
        boolean retryable,
        Long retryAfterMillis,
        String message,
        Map<String, Object> partialOutput,
        Map<String, Object> details,
        Map<String, Object> provenance
) {
    /**
     * Canonical constructor: validates required public fields and defensively copies map values.
     */
    public StructuredFailure {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("failure code is required");
        }
        category = Objects.requireNonNull(category, "failure category is required");
        if (retryAfterMillis != null && retryAfterMillis < 0) {
            throw new IllegalArgumentException("retryAfterMillis must be >= 0");
        }
        message = message == null ? "" : message;
        partialOutput = partialOutput == null ? Map.of() : Map.copyOf(partialOutput);
        details = details == null ? Map.of() : Map.copyOf(details);
        provenance = provenance == null ? Map.of() : Map.copyOf(provenance);
    }

    /**
     * Factory: creates a non-retryable failure without optional diagnostic payloads.
     */
    public static StructuredFailure terminal(String code, FailureCategory category, String message) {
        return new StructuredFailure(code, category, false, null, message, Map.of(), Map.of(), Map.of());
    }
}
