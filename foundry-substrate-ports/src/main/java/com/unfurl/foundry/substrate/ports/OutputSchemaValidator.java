package com.unfurl.foundry.substrate.ports;

import com.unfurl.substrate.policy.ExecutionContext;

/** Port: resolves a host-owned schema reference and validates structured output shape. */
@FunctionalInterface
public interface OutputSchemaValidator {
    /** Validates a value structurally and returns precise issues instead of throwing expected failures. */
    ValidationResult validate(String schemaRef, Object value, ExecutionContext context);
}
