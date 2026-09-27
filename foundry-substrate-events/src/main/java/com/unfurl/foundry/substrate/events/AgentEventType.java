package com.unfurl.foundry.substrate.events;

/**
 * enum for the Foundry AI substrate surface; documents the AgentEventType contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public enum AgentEventType {
    AGENT_STARTED,
    AGENT_COMPLETED,
    AGENT_FAILED,
    AGENT_CANCELLED,
    PHASE_STARTED,
    PHASE_COMPLETED,
    PHASE_FAILED,
    PHASE_SKIPPED,
    MODEL_INVOKED,
    TOKENS_CONSUMED,
    TOOL_CALLED,
    TOOL_COMPLETED,
    TOOL_FAILED,
    RAG_RETRIEVED,
    GUARDRAIL_TRIPPED,
    OUTPUT_VALIDATION_FAILED,
    OUTPUT_CORRECTION_REQUESTED
}
