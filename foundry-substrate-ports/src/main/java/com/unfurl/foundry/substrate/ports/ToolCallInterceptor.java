package com.unfurl.foundry.substrate.ports;

import com.unfurl.substrate.policy.ExecutionContext;

/** Chain-of-Responsibility element surrounding one neutral tool invocation. */
public interface ToolCallInterceptor {
    /** Evaluates or normalizes a call before registry resolution and side effects. */
    ToolCallDecision before(ToolCallRequest request, ExecutionContext context);

    /** Normalizes, redacts, or annotates a returned result after execution. */
    ToolCallResult after(ToolCallRequest request, ToolCallResult result, ExecutionContext context);
}
