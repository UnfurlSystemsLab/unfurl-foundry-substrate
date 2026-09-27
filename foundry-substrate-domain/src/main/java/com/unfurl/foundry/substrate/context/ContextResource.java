package com.unfurl.foundry.substrate.context;

import java.util.Map;

/**
 * Value Object: immutable read-only material selected through a context resource port.
 */
public record ContextResource(
        String id,
        String mediaType,
        Object content,
        String contentRef,
        Map<String, Object> provenance,
        Map<String, Object> metadata
) {
    /** Canonical constructor: requires stable identity and exactly one content representation. */
    public ContextResource {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("resource id is required");
        if (mediaType == null || mediaType.isBlank()) throw new IllegalArgumentException("mediaType is required");
        if ((content == null) == (contentRef == null || contentRef.isBlank())) {
            throw new IllegalArgumentException("exactly one of content or contentRef is required");
        }
        provenance = provenance == null ? Map.of() : Map.copyOf(provenance);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
