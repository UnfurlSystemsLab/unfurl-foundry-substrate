package com.unfurl.foundry.substrate.engine;

import com.unfurl.substrate.policy.ExecutionContext;

import java.util.Optional;
import java.util.Map;
import com.unfurl.foundry.substrate.runstate.AgentHarnessStatus;
import java.util.concurrent.ConcurrentHashMap;

/** Adapter: supplies process-local harness state storage for the embedded runtime default. */
public final class InMemoryAgentHarnessStateStore implements AgentHarnessStateStore {
    private final ConcurrentHashMap<String, AgentHarnessExecution> executions = new ConcurrentHashMap<>();

    /** Store operation: replaces the latest immutable execution for one run. */
    @Override public void save(AgentHarnessExecution execution, ExecutionContext context) {
        executions.compute(key(execution.state().runId(), context), (key, current) -> {
            if (current != null && current.state().status() == AgentHarnessStatus.CANCELLED
                    && execution.state().status() != AgentHarnessStatus.CANCELLED) {
                throw new IllegalStateException("cancelled harness cannot be overwritten");
            }
            return execution;
        });
    }

    /** Load operation: returns the latest process-local execution when present. */
    @Override public Optional<AgentHarnessExecution> load(String runId, ExecutionContext context) {
        return Optional.ofNullable(executions.get(key(runId, context)));
    }

    /** Atomic transition: only the process-local execution observed by the caller may advance. */
    @Override public Optional<AgentHarnessExecution> transition(AgentHarnessExecution expected,
            AgentHarnessExecution updated, Map<String, Object> signal, ExecutionContext context) {
        if (!expected.definition().equals(updated.definition())
                || !expected.state().runId().equals(updated.state().runId())) {
            throw new IllegalArgumentException("harness transition identity changed");
        }
        return executions.replace(key(expected.state().runId(), context), expected, updated)
                ? Optional.of(updated) : Optional.empty();
    }

    /** Tenant/run projector: isolates executions without imposing host identity requirements. */
    private String key(String runId, ExecutionContext context) {
        return String.valueOf(context == null ? null : context.tenantId()) + "\u0000" + runId;
    }
}
