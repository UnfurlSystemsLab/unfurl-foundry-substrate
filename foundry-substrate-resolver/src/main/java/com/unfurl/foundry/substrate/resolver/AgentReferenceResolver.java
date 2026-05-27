package com.unfurl.foundry.substrate.resolver;

import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.tool.ToolDefinition;

import java.util.Map;
import java.util.Optional;

/**
 * Resolves {@code agentRef} / {@code toolRef} string identifiers to definitions. In a host,
 * these are resolved at load/deploy time (flow provides the resolution mechanism; the agent
 * semantics are foundry's). This is a pure lookup over a supplied registry — no discovery,
 * no I/O.
 */
public final class AgentReferenceResolver {
    private final Map<String, AgentDefinition> agentsByRef;
    private final Map<String, ToolDefinition> toolsByRef;

    public AgentReferenceResolver(Map<String, AgentDefinition> agentsByRef, Map<String, ToolDefinition> toolsByRef) {
        this.agentsByRef = Map.copyOf(agentsByRef);
        this.toolsByRef = Map.copyOf(toolsByRef);
    }

    public Optional<AgentDefinition> resolveAgent(String agentRef) {
        return Optional.ofNullable(agentsByRef.get(agentRef));
    }

    public Optional<ToolDefinition> resolveTool(String toolRef) {
        return Optional.ofNullable(toolsByRef.get(toolRef));
    }
}
