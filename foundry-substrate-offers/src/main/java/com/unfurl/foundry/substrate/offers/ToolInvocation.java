package com.unfurl.foundry.substrate.offers;

import com.unfurl.foundry.substrate.ports.ToolCallRequest;
import com.unfurl.foundry.substrate.ports.ToolCallResult;
import com.unfurl.foundry.substrate.ports.ToolExecutor;
import com.unfurl.substrate.composition.ContractInvocable;
import com.unfurl.substrate.composition.ContractInvocation;
import com.unfurl.substrate.composition.ContractInvocationResult;
import com.unfurl.substrate.policy.ExecutionContext;

/** Exposes the {@code tool.call} capability over a frozen DCP contract. */
/**
 * class for the Foundry AI substrate surface; documents the ToolInvocation contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public final class ToolInvocation implements ContractInvocable {
    private final String contractId;
    private final String contractVersion;
    private final String toolName;
    private final ToolExecutor executor;

/**
 * Constructs ToolInvocation with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public ToolInvocation(String contractId, String contractVersion, String toolName, ToolExecutor executor) {
        this.contractId = contractId;
        this.contractVersion = contractVersion;
        this.toolName = toolName;
        this.executor = executor;
    }

/**
 * Implements the contractId helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    @Override
    public String contractId() {
        return contractId;
    }

/**
 * Implements the contractVersion helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    @Override
    public String contractVersion() {
        return contractVersion;
    }

/**
 * Performs the invoke operation for this component, translating validated inputs into the domain result expected by callers.
 */
    @Override
    public ContractInvocationResult invoke(ContractInvocation invocation, ExecutionContext context) {
        ToolCallResult result = executor.execute(
                new ToolCallRequest(invocation.correlationId(), toolName, invocation.input(), invocation.metadata()),
                context);
        if (!result.success()) {
            // Adapter boundary: the current DCP compatibility envelope carries code/message;
            // the canonical category, retry, partial-output, and provenance data remain on result.failure().
            return ContractInvocationResult.failure(result.errorCode(), result.errorMessage());
        }
        return ContractInvocationResult.success(result.output());
    }
}
