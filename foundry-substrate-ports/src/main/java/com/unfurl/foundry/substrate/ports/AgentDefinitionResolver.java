package com.unfurl.foundry.substrate.ports;

import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.substrate.policy.ExecutionContext;
import java.util.Optional;

/** Repository port: resolves only exact, pinned agent definition references for delegation. */
public interface AgentDefinitionResolver {
    /** Resolves an exact id and version without applying mutable latest-version semantics. */
    Optional<AgentDefinition> resolve(String agentId, String version, ExecutionContext context);
}
