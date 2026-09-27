package com.unfurl.foundry.substrate.ports;

import com.unfurl.substrate.policy.ExecutionContext;

import java.util.Map;

/** Port: checks domain meaning after structural validation and output mapping succeed. */
@FunctionalInterface
public interface SemanticValidator {
    /** Validates the mapped value against its raw structured source without performing correction. */
    ValidationResult validate(Object value, Map<String, Object> source, ExecutionContext context);
}
