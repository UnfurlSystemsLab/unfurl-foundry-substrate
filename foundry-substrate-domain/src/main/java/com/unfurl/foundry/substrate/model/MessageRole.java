package com.unfurl.foundry.substrate.model;

/**
 * enum for the Foundry AI substrate surface; documents the MessageRole contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public enum MessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}
