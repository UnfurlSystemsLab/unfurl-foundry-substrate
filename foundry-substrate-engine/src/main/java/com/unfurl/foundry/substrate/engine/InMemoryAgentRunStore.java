package com.unfurl.foundry.substrate.engine;

import com.unfurl.foundry.substrate.runstate.AgentRunState;
import com.unfurl.substrate.policy.ExecutionContext;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory run store. Does not persist to disk and does not coordinate across processes. */
/**
 * class for the Foundry AI substrate surface; documents the InMemoryAgentRunStore contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public final class InMemoryAgentRunStore implements AgentRunStore {
    private final ConcurrentHashMap<String, AgentRunState> runs = new ConcurrentHashMap<>();

/**
 * Performs the save operation for this component, translating validated inputs into the domain result expected by callers.
 */
    @Override
    public void save(AgentRunState run, ExecutionContext context) {
        runs.put(run.runId(), run);
    }

/**
 * Performs the load operation for this component, translating validated inputs into the domain result expected by callers.
 */
    @Override
    public Optional<AgentRunState> load(String runId, ExecutionContext context) {
        return Optional.ofNullable(runs.get(runId));
    }
}
