package com.unfurl.foundry.substrate.guardrail;

import com.unfurl.substrate.policy.ExecutionContext;

import java.util.Map;

/**
 * A per-call tool-permission decision. Policy resolution and persisted approvals live in
 * {@code unfurl-foundry}; the substrate only consults the decision.
 */
public interface PermissionBridge {
    PermissionDecision check(String toolName, Map<String, Object> arguments, ExecutionContext context);
}
