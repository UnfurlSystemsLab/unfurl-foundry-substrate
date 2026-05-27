package com.unfurl.foundry.substrate.ports;

import com.unfurl.substrate.policy.ExecutionContext;

public interface ToolExecutor {
    ToolCallResult execute(ToolCallRequest request, ExecutionContext context);
}
