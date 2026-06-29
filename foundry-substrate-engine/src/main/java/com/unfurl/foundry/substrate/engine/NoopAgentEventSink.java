package com.unfurl.foundry.substrate.engine;

import com.unfurl.foundry.substrate.events.AgentEvent;
import com.unfurl.foundry.substrate.ports.AgentEventSink;
import com.unfurl.substrate.policy.ExecutionContext;

/** Default event sink that performs no I/O and never phones home. */
/**
 * class for the Foundry AI substrate surface; documents the NoopAgentEventSink contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public final class NoopAgentEventSink implements AgentEventSink {
/**
 * Implements the publish helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    @Override
    public void publish(AgentEvent event, ExecutionContext context) {
        // intentionally empty
    }
}
