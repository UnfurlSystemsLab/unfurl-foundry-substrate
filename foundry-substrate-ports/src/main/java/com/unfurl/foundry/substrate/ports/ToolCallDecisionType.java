package com.unfurl.foundry.substrate.ports;

/** Decision vocabulary for deterministic policy evaluation before a tool side effect. */
public enum ToolCallDecisionType {
    ALLOW,
    DENY,
    REQUIRE_APPROVAL
}
