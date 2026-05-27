package com.unfurl.foundry.substrate.guardrail;

public record PermissionDecision(
        boolean allowed,
        boolean requiresApproval,
        String reason
) {
    public static PermissionDecision allow() {
        return new PermissionDecision(true, false, null);
    }

    public static PermissionDecision requireApproval(String reason) {
        return new PermissionDecision(false, true, reason);
    }

    public static PermissionDecision deny(String reason) {
        return new PermissionDecision(false, false, reason);
    }
}
