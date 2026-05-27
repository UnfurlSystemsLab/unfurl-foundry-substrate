package com.unfurl.foundry.substrate.offers;

import com.unfurl.foundry.substrate.ports.ToolCallRequest;
import com.unfurl.foundry.substrate.ports.ToolCallResult;
import com.unfurl.foundry.substrate.ports.ToolExecutor;
import com.unfurl.substrate.composition.ContractInvocable;
import com.unfurl.substrate.composition.ContractInvocation;
import com.unfurl.substrate.composition.ContractInvocationResult;
import com.unfurl.substrate.policy.ExecutionContext;

/** Exposes the {@code tool.call} capability over a frozen DCP contract. */
public final class ToolInvocation implements ContractInvocable {
    private final String contractId;
    private final String contractVersion;
    private final String toolName;
    private final ToolExecutor executor;

    public ToolInvocation(String contractId, String contractVersion, String toolName, ToolExecutor executor) {
        this.contractId = contractId;
        this.contractVersion = contractVersion;
        this.toolName = toolName;
        this.executor = executor;
    }

    @Override
    public String contractId() {
        return contractId;
    }

    @Override
    public String contractVersion() {
        return contractVersion;
    }

    @Override
    public ContractInvocationResult invoke(ContractInvocation invocation, ExecutionContext context) {
        ToolCallResult result = executor.execute(
                new ToolCallRequest(invocation.correlationId(), toolName, invocation.input(), invocation.metadata()),
                context);
        if (!result.success()) {
            return ContractInvocationResult.failure(result.errorCode(), result.errorMessage());
        }
        return ContractInvocationResult.success(result.output());
    }
}
