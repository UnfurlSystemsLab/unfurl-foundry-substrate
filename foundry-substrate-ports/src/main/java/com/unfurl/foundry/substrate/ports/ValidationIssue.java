package com.unfurl.foundry.substrate.ports;

import java.util.Map;

/** Value Object: one precise, sanitized structural or semantic validation problem. */
public record ValidationIssue(
        String code,
        String path,
        String message,
        Map<String, Object> metadata
) {
    /** Freezes issue metadata and supplies stable non-null diagnostic fields. */
    public ValidationIssue {
        code = code == null || code.isBlank() ? "VALIDATION_ISSUE" : code;
        path = path == null || path.isBlank() ? "$" : path;
        message = message == null ? "validation failed" : message;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
