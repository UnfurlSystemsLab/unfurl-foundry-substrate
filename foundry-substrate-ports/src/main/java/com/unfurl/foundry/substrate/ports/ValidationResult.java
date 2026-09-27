package com.unfurl.foundry.substrate.ports;

import java.util.List;
import java.util.Map;

/** Value Object: deterministic validation decision with correction-safe issue details. */
public record ValidationResult(
        boolean valid,
        List<ValidationIssue> issues,
        Map<String, Object> metadata
) {
    /** Enforces agreement between validity and issue presence and freezes collections. */
    public ValidationResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if (valid && !issues.isEmpty()) {
            throw new IllegalArgumentException("valid result cannot contain issues");
        }
        if (!valid && issues.isEmpty()) {
            throw new IllegalArgumentException("invalid result requires at least one issue");
        }
    }

    /** Factory: creates a successful validation result. */
    public static ValidationResult success() {
        return new ValidationResult(true, List.of(), Map.of());
    }

    /** Factory: creates a failed result from one or more precise issues. */
    public static ValidationResult invalid(List<ValidationIssue> issues) {
        return new ValidationResult(false, issues, Map.of());
    }
}
