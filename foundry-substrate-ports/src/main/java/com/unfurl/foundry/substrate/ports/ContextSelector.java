package com.unfurl.foundry.substrate.ports;

import com.unfurl.foundry.substrate.context.ContextSelectionRequest;
import com.unfurl.foundry.substrate.context.ContextSelectionResult;
import com.unfurl.substrate.policy.ExecutionContext;

/** Strategy port: selects bounded context exclusively from explicit request material. */
public interface ContextSelector {
    /** Selects reproducible context under the request policy or fails closed. */
    ContextSelectionResult select(ContextSelectionRequest request, ExecutionContext context);
}
