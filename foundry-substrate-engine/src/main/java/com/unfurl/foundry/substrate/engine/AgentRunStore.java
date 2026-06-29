package com.unfurl.foundry.substrate.engine;

import com.unfurl.foundry.substrate.runstate.AgentRunState;
import com.unfurl.substrate.policy.ExecutionContext;

import java.util.Optional;

/**
 * interface for the Foundry AI substrate surface; documents the AgentRunStore contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public interface AgentRunStore {
    void save(AgentRunState run, ExecutionContext context);

    Optional<AgentRunState> load(String runId, ExecutionContext context);
}
