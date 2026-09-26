package com.unfurl.foundry.substrate.runstate;

/**
 * State vocabulary for a bounded agent harness run. Durable products may
 * persist these states, while the substrate uses them for in-memory execution.
 */
public enum AgentHarnessStatus {
    RUNNING,
    COMPLETED,
    WAITING_FOR_USER,
    WAITING_FOR_APPROVAL,
    ESCALATED,
    GAP,
    FAILED,
    CANCELLED
}
