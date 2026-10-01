package com.unfurl.foundry.substrate.ports;

import com.unfurl.foundry.substrate.runstate.ToolSuspension;
import com.unfurl.substrate.policy.ExecutionContext;

/** Host Strategy: supplies one original attempt's raw result by authorized at-most-once dispatch or exact proven hydration. */
@FunctionalInterface
public interface SuspendedToolExecutor {
    /** Original-attempt operation: retains normalized scope/arguments; host owns grant/claim/journal checks, never guesses uncertain results. */
    ToolCallResult execute(ToolSuspension suspension, ToolCallRequest normalizedRequest, ExecutionContext context);
}
