package com.unfurl.foundry.substrate.ports;

import com.unfurl.foundry.substrate.context.ContextResource;
import com.unfurl.substrate.policy.ExecutionContext;

/** Port: reads explicitly referenced context resources without granting discovery authority. */
public interface ContextResourceProvider {
    /** Reads one stable resource reference in the caller's execution context. */
    ContextResource read(String resourceRef, ExecutionContext context);
}
