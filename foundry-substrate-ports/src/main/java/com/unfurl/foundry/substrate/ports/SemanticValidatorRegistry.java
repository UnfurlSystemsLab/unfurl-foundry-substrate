package com.unfurl.foundry.substrate.ports;

import com.unfurl.substrate.policy.ExecutionContext;

import java.util.Optional;

/** Port: resolves named semantic-validation strategies within the active tenant/runtime profile. */
@FunctionalInterface
public interface SemanticValidatorRegistry {
    /** Returns the validator bound to a stable profile reference, or empty when no binding exists. */
    Optional<SemanticValidator> resolve(String validatorRef, ExecutionContext context);
}
