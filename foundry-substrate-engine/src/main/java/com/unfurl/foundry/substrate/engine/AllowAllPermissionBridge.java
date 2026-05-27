package com.unfurl.foundry.substrate.engine;

import com.unfurl.foundry.substrate.guardrail.PermissionBridge;
import com.unfurl.foundry.substrate.guardrail.PermissionDecision;
import com.unfurl.substrate.policy.ExecutionContext;

import java.util.Map;

/** Default permission bridge that always allows. Real policy lives in unfurl-foundry. */
public final class AllowAllPermissionBridge implements PermissionBridge {
    @Override
    public PermissionDecision check(String toolName, Map<String, Object> arguments, ExecutionContext context) {
        return PermissionDecision.allow();
    }
}
