package com.unfurl.foundry.substrate.ports;

import com.unfurl.foundry.substrate.runstate.*;
import com.unfurl.substrate.policy.ExecutionContext;

/** Projection SPI: interprets the same continued child after the host exclusively claims the harness; it never authorizes child effects. */
public interface AgentHarnessChildContinuation {
    /** Same-child projection: replaces the last waiting observation without spending another turn; only genuine continue output schedules a new bounded turn. */
    AgentHarnessRunState continueChild(String runId, AgentRunState child, ExecutionContext context);
}
