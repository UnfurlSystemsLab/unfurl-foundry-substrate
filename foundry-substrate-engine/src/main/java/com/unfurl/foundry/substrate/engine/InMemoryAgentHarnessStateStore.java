package com.unfurl.foundry.substrate.engine;

import com.unfurl.substrate.policy.ExecutionContext;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Adapter: supplies process-local harness state storage for the embedded runtime default. */
public final class InMemoryAgentHarnessStateStore implements AgentHarnessStateStore {
    private final ConcurrentHashMap<String, AgentHarnessExecution> executions = new ConcurrentHashMap<>();

    /** Store operation: replaces the latest immutable execution for one run. */
    @Override public void save(AgentHarnessExecution execution, ExecutionContext context) {
        executions.put(execution.state().runId(), execution);
    }

    /** Load operation: returns the latest process-local execution when present. */
    @Override public Optional<AgentHarnessExecution> load(String runId, ExecutionContext context) {
        return Optional.ofNullable(executions.get(runId));
    }
}
