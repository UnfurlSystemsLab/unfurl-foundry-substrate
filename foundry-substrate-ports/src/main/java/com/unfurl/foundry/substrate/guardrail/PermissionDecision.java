package com.unfurl.foundry.substrate.guardrail;

/**
 * record for the Foundry AI substrate surface; documents the PermissionDecision contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public record PermissionDecision(
        boolean allowed,
        boolean requiresApproval,
        String reason
) {
/**
 * Factory method: creates the allow result while keeping caller-facing defaults and validation in one place.
 */
    public static PermissionDecision allow() {
        return new PermissionDecision(true, false, null);
    }

/**
 * Factory method: creates the requireApproval result while keeping caller-facing defaults and validation in one place.
 */
    public static PermissionDecision requireApproval(String reason) {
        return new PermissionDecision(false, true, reason);
    }

/**
 * Factory method: creates the deny result while keeping caller-facing defaults and validation in one place.
 */
    public static PermissionDecision deny(String reason) {
        return new PermissionDecision(false, false, reason);
    }
}
