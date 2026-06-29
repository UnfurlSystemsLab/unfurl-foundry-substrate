package com.unfurl.foundry.substrate.engine;

import com.unfurl.foundry.substrate.guardrail.PermissionBridge;
import com.unfurl.foundry.substrate.guardrail.PermissionDecision;
import com.unfurl.substrate.policy.ExecutionContext;

import java.util.Map;

/** Default permission bridge that always allows. Real policy lives in unfurl-foundry. */
/**
 * class for the Foundry AI substrate surface; documents the AllowAllPermissionBridge contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public final class AllowAllPermissionBridge implements PermissionBridge {
/**
 * Implements the check helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    @Override
    public PermissionDecision check(String toolName, Map<String, Object> arguments, ExecutionContext context) {
        return PermissionDecision.allow();
    }
}
