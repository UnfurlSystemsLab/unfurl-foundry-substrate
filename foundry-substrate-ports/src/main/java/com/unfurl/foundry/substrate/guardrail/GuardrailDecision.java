package com.unfurl.foundry.substrate.guardrail;

public record GuardrailDecision(
        boolean allowed,
        String reason
) {
    public static GuardrailDecision allow() {
        return new GuardrailDecision(true, null);
    }

    public static GuardrailDecision deny(String reason) {
        return new GuardrailDecision(false, reason);
    }
}
