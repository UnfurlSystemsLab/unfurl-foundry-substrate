package com.unfurl.foundry.substrate.ports;

import com.unfurl.foundry.substrate.agent.AgentHarnessDefinition;
import com.unfurl.foundry.substrate.runstate.AgentHarnessRunState;
import com.unfurl.substrate.policy.ExecutionContext;

import java.util.Map;

/**
 * Port: runs a bounded harness around an agent. The substrate provides an
 * embedded in-memory implementation; Foundry may wrap the same model with
 * durability, server APIs, and deployment lifecycle.
 */
public interface AgentHarnessRuntime {
    /**
     * Starts a harness run with caller-supplied input and returns the terminal
     * or waiting snapshot produced by the bounded loop.
     */
    AgentHarnessRunState start(AgentHarnessDefinition harness, Map<String, Object> input, ExecutionContext context);

    /**
     * Resumes a waiting harness run with an external signal when the runtime has
     * retained enough state to continue safely.
     */
    AgentHarnessRunState resume(String runId, Map<String, Object> signal, ExecutionContext context);

    /**
     * Cancels a harness run known to this runtime and returns the latest
     * cancelled snapshot.
     */
    AgentHarnessRunState cancel(String runId, ExecutionContext context);
}
