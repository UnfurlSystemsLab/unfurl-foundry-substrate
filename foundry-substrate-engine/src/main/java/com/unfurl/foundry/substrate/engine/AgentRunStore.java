package com.unfurl.foundry.substrate.engine;

import com.unfurl.foundry.substrate.runstate.AgentRunState;
import com.unfurl.substrate.policy.ExecutionContext;

import java.util.Optional;

public interface AgentRunStore {
    void save(AgentRunState run, ExecutionContext context);

    Optional<AgentRunState> load(String runId, ExecutionContext context);
}
