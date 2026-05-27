package com.unfurl.foundry.substrate.engine;

import com.unfurl.foundry.substrate.events.AgentEvent;
import com.unfurl.foundry.substrate.ports.AgentEventSink;
import com.unfurl.substrate.policy.ExecutionContext;

/** Default event sink that performs no I/O and never phones home. */
public final class NoopAgentEventSink implements AgentEventSink {
    @Override
    public void publish(AgentEvent event, ExecutionContext context) {
        // intentionally empty
    }
}
