package com.unfurl.foundry.substrate.model;

/** Neutral token-usage tally reported by a model invocation. */
/**
 * record for the Foundry AI substrate surface; documents the ModelUsage contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public record ModelUsage(
        long promptTokens,
        long completionTokens
) {
/**
 * Implements the totalTokens helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    public long totalTokens() {
        return promptTokens + completionTokens;
    }

/**
 * Implements the zero helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    public static ModelUsage zero() {
        return new ModelUsage(0, 0);
    }
}
