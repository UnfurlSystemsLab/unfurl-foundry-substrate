package com.unfurl.foundry.substrate.ports;

import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.agent.AgentPhase;
import com.unfurl.foundry.substrate.model.ModelRequest;
import com.unfurl.substrate.policy.ExecutionContext;
import java.util.Map;

/** Strategy port: projects explicit model context before the durable boundary, without owning providers or persistence. */
@FunctionalInterface
public interface ModelRequestProjector {
    /** Returns the final request for one initial/follow-up/correction call; rejection prevents external dispatch. */
    ModelRequest project(AgentDefinition agent, AgentPhase phase, Map<String, Object> invocationInput, Map<String, Object> input,
                         ModelRequest request, ExecutionContext context);

    /** Identity Strategy: explicit no-compaction behavior for neutral embedded hosts, never a product binding fallback. */
    static ModelRequestProjector identity() { return (agent, phase, invocationInput, input, request, context) -> request; }
}
