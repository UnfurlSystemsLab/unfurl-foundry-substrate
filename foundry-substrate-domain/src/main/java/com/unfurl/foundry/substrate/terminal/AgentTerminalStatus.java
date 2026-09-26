package com.unfurl.foundry.substrate.terminal;

/**
 * Provider-neutral terminal status returned by an agent harness or DCP agent invocation.
 */
public enum AgentTerminalStatus {
    COMPLETED,
    WAITING_FOR_USER,
    WAITING_FOR_APPROVAL,
    ESCALATED,
    GAP,
    FAILED,
    CANCELLED
}
