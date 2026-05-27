package com.unfurl.foundry.substrate.engine;

import com.unfurl.foundry.substrate.runstate.AgentRunState;
import com.unfurl.substrate.policy.ExecutionContext;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory run store. Does not persist to disk and does not coordinate across processes. */
public final class InMemoryAgentRunStore implements AgentRunStore {
    private final ConcurrentHashMap<String, AgentRunState> runs = new ConcurrentHashMap<>();

    @Override
    public void save(AgentRunState run, ExecutionContext context) {
        runs.put(run.runId(), run);
    }

    @Override
    public Optional<AgentRunState> load(String runId, ExecutionContext context) {
        return Optional.ofNullable(runs.get(runId));
    }
}
