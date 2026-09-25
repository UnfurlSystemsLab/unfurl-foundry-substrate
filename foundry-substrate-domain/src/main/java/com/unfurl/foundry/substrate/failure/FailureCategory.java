package com.unfurl.foundry.substrate.failure;

/**
 * Provider-neutral classification used by tool, validation, delegation, and runtime failures.
 */
public enum FailureCategory {
    VALIDATION,
    AUTHORIZATION,
    NOT_FOUND,
    BUSINESS_RULE,
    RATE_LIMIT,
    TRANSIENT,
    PROVIDER,
    INTERNAL
}
