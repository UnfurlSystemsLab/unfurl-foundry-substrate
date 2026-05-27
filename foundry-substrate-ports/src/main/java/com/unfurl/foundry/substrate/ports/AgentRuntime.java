package com.unfurl.foundry.substrate.ports;

import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.runstate.AgentRunState;
import com.unfurl.substrate.policy.ExecutionContext;

import java.util.Map;

/**
 * Port for running a multi-phase agent. The substrate ships a minimal in-process
 * implementation ({@code EmbeddedAgentRuntime}); durable/distributed/streaming runtimes
 * live in {@code unfurl-foundry}.
 */
public interface AgentRuntime {
    AgentRunState start(AgentDefinition agent, Map<String, Object> input, ExecutionContext context);

    AgentRunState resume(String runId, Map<String, Object> signal, ExecutionContext context);

    AgentRunState cancel(String runId, ExecutionContext context);
}
