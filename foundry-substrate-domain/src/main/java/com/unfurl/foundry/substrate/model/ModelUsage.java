package com.unfurl.foundry.substrate.model;

/** Neutral token-usage tally reported by a model invocation. */
public record ModelUsage(
        long promptTokens,
        long completionTokens
) {
    public long totalTokens() {
        return promptTokens + completionTokens;
    }

    public static ModelUsage zero() {
        return new ModelUsage(0, 0);
    }
}
