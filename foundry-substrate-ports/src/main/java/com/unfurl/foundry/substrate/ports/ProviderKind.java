package com.unfurl.foundry.substrate.ports;

/**
 * enum for the Foundry AI substrate surface; documents the ProviderKind contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public enum ProviderKind {
    LLM,
    EMBEDDER,
    EXTRACTOR
}
