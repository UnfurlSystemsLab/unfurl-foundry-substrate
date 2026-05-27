package com.unfurl.foundry.substrate.ports;

import com.unfurl.foundry.substrate.events.AgentEvent;
import com.unfurl.substrate.policy.ExecutionContext;

public interface AgentEventSink {
    void publish(AgentEvent event, ExecutionContext context);
}
