package com.unfurl.foundry.substrate.guardrail;

/**
 * record for the Foundry AI substrate surface; documents the GuardrailDecision contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public record GuardrailDecision(
        boolean allowed,
        String reason
) {
/**
 * Factory method: creates the allow result while keeping caller-facing defaults and validation in one place.
 */
    public static GuardrailDecision allow() {
        return new GuardrailDecision(true, null);
    }

/**
 * Factory method: creates the deny result while keeping caller-facing defaults and validation in one place.
 */
    public static GuardrailDecision deny(String reason) {
        return new GuardrailDecision(false, reason);
    }
}
