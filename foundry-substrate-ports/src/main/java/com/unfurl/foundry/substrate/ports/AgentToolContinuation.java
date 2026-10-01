package com.unfurl.foundry.substrate.ports;

import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.runstate.AgentRunState;
import com.unfurl.substrate.policy.ExecutionContext;

/** Execution SPI: continues a pre-validation tool wait after a host establishes exclusive authority; ordinary resume is still a query. */
public interface AgentToolContinuation {
    /** Continuation operation: uses pinned definition and exact saved state; host owns atomic claiming, effect safety and frozen port validation. */
    AgentRunState continueTool(AgentDefinition definition, AgentRunState waiting,
            SuspendedToolExecutor executor, ExecutionContext context);
}
