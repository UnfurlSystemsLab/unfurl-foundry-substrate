package com.unfurl.foundry.substrate.offers;

import com.unfurl.foundry.substrate.model.Message;
import com.unfurl.foundry.substrate.model.ModelRequest;
import com.unfurl.foundry.substrate.model.ModelResponse;
import com.unfurl.foundry.substrate.ports.ModelProvider;
import com.unfurl.substrate.composition.ContractInvocable;
import com.unfurl.substrate.composition.ContractInvocation;
import com.unfurl.substrate.composition.ContractInvocationResult;
import com.unfurl.substrate.policy.ExecutionContext;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Exposes the {@code provider.call} capability over a frozen DCP contract. */
public final class ProviderInvocation implements ContractInvocable {
    private final String contractId;
    private final String contractVersion;
    private final String modelRef;
    private final ModelProvider provider;

    public ProviderInvocation(String contractId, String contractVersion, String modelRef, ModelProvider provider) {
        this.contractId = contractId;
        this.contractVersion = contractVersion;
        this.modelRef = modelRef;
        this.provider = provider;
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
        String prompt = String.valueOf(invocation.input().getOrDefault("prompt", ""));
        ModelResponse response = provider.complete(new ModelRequest(
                List.of(Message.user(prompt)),
                modelRef,
                invocation.input(),
                List.of(),
                invocation.metadata()), context);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("message", response.message() == null ? "" : response.message().content());
        output.put("finishReason", response.finishReason());
        output.put("usage", response.usage());
        output.put("providerName", response.providerName());
        output.put("estimatedCostUsd", response.estimatedCostUsd());
        return ContractInvocationResult.success(output);
    }
}
