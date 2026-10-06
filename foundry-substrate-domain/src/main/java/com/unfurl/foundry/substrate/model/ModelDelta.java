package com.unfurl.foundry.substrate.model;

/**
 * Value Object: one ordered, incremental fragment of an assistant message's text, as a streaming provider produced it. Deltas are
 * a non-authoritative preview: the provider's returned {@link ModelResponse} remains the only authoritative result, and the
 * concatenation of a call's deltas is the text it was built from.
 */
public record ModelDelta(int index, String text) {
    /** Validating constructor: a nonnegative position within the call and a nonempty text fragment. */
    public ModelDelta {
        if (index < 0) throw new IllegalArgumentException("delta index must be nonnegative");
        if (text == null || text.isEmpty()) throw new IllegalArgumentException("delta text must be nonempty");
    }
}
