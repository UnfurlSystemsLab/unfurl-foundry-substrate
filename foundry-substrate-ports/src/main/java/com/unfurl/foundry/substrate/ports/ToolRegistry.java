package com.unfurl.foundry.substrate.ports;

import com.unfurl.substrate.policy.ExecutionContext;

import java.util.Optional;

public interface ToolRegistry {
    boolean hasTool(String toolName, ExecutionContext context);

    Optional<ToolExecutor> resolveTool(String toolName, ExecutionContext context);
}
