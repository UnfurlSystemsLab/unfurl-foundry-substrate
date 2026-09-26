package com.unfurl.foundry.substrate.ports;

import com.unfurl.foundry.substrate.failure.FailureCategory;
import com.unfurl.foundry.substrate.failure.StructuredFailure;

import java.util.Map;
import java.util.Objects;

/** Immutable policy decision carrying normalized arguments without widening caller permissions. */
public record ToolCallDecision(
        ToolCallDecisionType type,
        Map<String, Object> arguments,
        StructuredFailure failure,
        Map<String, Object> metadata
) {
    /** Validates decision/failure agreement and defensively freezes policy data. */
    public ToolCallDecision {
        type = Objects.requireNonNull(type, "type");
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if (type == ToolCallDecisionType.ALLOW && failure != null) {
            throw new IllegalArgumentException("ALLOW decision cannot contain a failure");
        }
        if (type != ToolCallDecisionType.ALLOW && failure == null) {
            throw new IllegalArgumentException(type + " decision requires a structured failure");
        }
    }

    /** Factory: permits execution with the supplied current argument map. */
    public static ToolCallDecision allow(Map<String, Object> arguments) {
        return new ToolCallDecision(ToolCallDecisionType.ALLOW, arguments, null, Map.of());
    }

    /** Factory: denies execution with an explicit structured policy failure. */
    public static ToolCallDecision deny(
            Map<String, Object> arguments, StructuredFailure failure, Map<String, Object> metadata) {
        return new ToolCallDecision(ToolCallDecisionType.DENY, arguments, failure, metadata);
    }

    /** Factory: suspends execution until a host supplies the named one-time approval token. */
    public static ToolCallDecision requireApproval(
            Map<String, Object> arguments, String approvalId, Map<String, Object> metadata) {
        if (approvalId == null || approvalId.isBlank()) {
            throw new IllegalArgumentException("approvalId must not be blank");
        }
        java.util.LinkedHashMap<String, Object> details = new java.util.LinkedHashMap<>(metadata == null
                ? Map.of() : metadata);
        details.put("approvalId", approvalId);
        StructuredFailure failure = new StructuredFailure(
                "TOOL_APPROVAL_REQUIRED", FailureCategory.AUTHORIZATION, false, null,
                "Tool call requires approval", Map.of("approvalId", approvalId), Map.copyOf(details), Map.of());
        return new ToolCallDecision(ToolCallDecisionType.REQUIRE_APPROVAL, arguments, failure, details);
    }
}
