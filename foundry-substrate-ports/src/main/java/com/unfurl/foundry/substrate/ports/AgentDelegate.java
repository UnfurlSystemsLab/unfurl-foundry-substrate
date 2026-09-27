package com.unfurl.foundry.substrate.ports;

import com.unfurl.foundry.substrate.delegation.AgentDelegationRequest;
import com.unfurl.foundry.substrate.delegation.AgentDelegationResult;
import com.unfurl.substrate.policy.ExecutionContext;

/** Strategy port: invokes a governed child agent through explicit projection and least authority. */
public interface AgentDelegate {
    /** Invokes one pinned child and returns its terminal or structured failure without flattening it. */
    AgentDelegationResult invoke(AgentDelegationRequest request, ExecutionContext context);
}
