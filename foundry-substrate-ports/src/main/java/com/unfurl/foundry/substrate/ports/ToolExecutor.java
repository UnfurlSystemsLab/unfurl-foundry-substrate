package com.unfurl.foundry.substrate.ports;

import com.unfurl.substrate.policy.ExecutionContext;

/**
 * interface for the Foundry AI substrate surface; documents the ToolExecutor contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public interface ToolExecutor {
    ToolCallResult execute(ToolCallRequest request, ExecutionContext context);
}
