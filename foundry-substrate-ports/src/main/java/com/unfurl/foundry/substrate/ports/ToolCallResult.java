package com.unfurl.foundry.substrate.ports;

import com.unfurl.foundry.substrate.failure.FailureCategory;
import com.unfurl.foundry.substrate.failure.StructuredFailure;

import java.util.Map;

/**
 * record for the Foundry AI substrate surface; documents the ToolCallResult contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public record ToolCallResult(
        boolean success,
        Map<String, Object> output,
        String errorCode,
        String errorMessage,
        StructuredFailure failure
) {
    /**
     * Compatibility constructor: preserves the former four-field Java contract and promotes legacy
     * failures into the canonical structured failure representation.
     */
    public ToolCallResult(boolean success, Map<String, Object> output, String errorCode, String errorMessage) {
        this(success, output, errorCode, errorMessage, null);
    }

    /**
     * Canonical constructor: enforces agreement between success, legacy aliases, and structured failure.
     */
    public ToolCallResult {
        output = output == null ? Map.of() : Map.copyOf(output);
        if (success) {
            if (failure != null) {
                throw new IllegalArgumentException("successful tool result cannot contain failure");
            }
            errorCode = null;
            errorMessage = null;
        } else {
            failure = failure == null
                    ? StructuredFailure.terminal(
                    errorCode == null || errorCode.isBlank() ? "TOOL_FAILED" : errorCode,
                    FailureCategory.INTERNAL,
                    errorMessage)
                    : failure;
            errorCode = failure.code();
            errorMessage = failure.message();
            if (output.isEmpty() && !failure.partialOutput().isEmpty()) {
                output = failure.partialOutput();
            }
        }
    }

/**
 * Factory method: creates the success result while keeping caller-facing defaults and validation in one place.
 */
    public static ToolCallResult success(Map<String, Object> output) {
        return new ToolCallResult(true, output, null, null, null);
    }

    /**
     * Factory: represents a successful call with an intentionally empty result.
     */
    public static ToolCallResult emptySuccess() {
        return success(Map.of());
    }

/**
 * Factory method: creates the failure result while keeping caller-facing defaults and validation in one place.
 */
    public static ToolCallResult failure(String code, String message) {
        return failure(StructuredFailure.terminal(
                code == null || code.isBlank() ? "TOOL_FAILED" : code,
                FailureCategory.INTERNAL,
                message));
    }

    /**
     * Factory: creates a failed result whose structured failure is the canonical error payload.
     */
    public static ToolCallResult failure(StructuredFailure failure) {
        StructuredFailure required = java.util.Objects.requireNonNull(failure, "failure");
        return new ToolCallResult(false, required.partialOutput(), required.code(), required.message(), required);
    }
}
