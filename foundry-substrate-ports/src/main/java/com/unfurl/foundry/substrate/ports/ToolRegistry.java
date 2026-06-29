package com.unfurl.foundry.substrate.ports;

import com.unfurl.substrate.policy.ExecutionContext;

import java.util.Optional;

/**
 * interface for the Foundry AI substrate surface; documents the ToolRegistry contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public interface ToolRegistry {
    boolean hasTool(String toolName, ExecutionContext context);

    Optional<ToolExecutor> resolveTool(String toolName, ExecutionContext context);
}
