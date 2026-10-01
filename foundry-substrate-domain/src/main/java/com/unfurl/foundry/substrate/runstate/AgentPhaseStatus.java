package com.unfurl.foundry.substrate.runstate;

/**
 * enum for the Foundry AI substrate surface; documents the AgentPhaseStatus contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public enum AgentPhaseStatus {
    PENDING,
    RUNNING,
    WAITING,
    COMPLETED,
    FAILED,
    SKIPPED,
    CANCELLED
}
