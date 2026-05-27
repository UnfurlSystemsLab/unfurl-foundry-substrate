package com.unfurl.foundry.substrate.offers;

import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.ports.AgentRuntime;
import com.unfurl.foundry.substrate.runstate.AgentPhaseState;
import com.unfurl.foundry.substrate.runstate.AgentRunState;
import com.unfurl.foundry.substrate.runstate.AgentRunStatus;
import com.unfurl.substrate.composition.ContractInvocable;
import com.unfurl.substrate.composition.ContractInvocation;
import com.unfurl.substrate.composition.ContractInvocationResult;
import com.unfurl.substrate.policy.ExecutionContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exposes the {@code agent.run} capability over a frozen DCP contract. The {@code unfurl-dcp}
 * broker registers this {@link ContractInvocable} into a host's CapabilityRegistry on accept;
 * it translates the frozen-contract invocation onto {@link AgentRuntime#start} and maps the
 * resulting run state back to a structured result.
 */
public final class AgentInvocation implements ContractInvocable {
    private final String contractId;
    private final String contractVersion;
    private final AgentDefinition agent;
    private final AgentRuntime runtime;

    public AgentInvocation(String contractId, String contractVersion, AgentDefinition agent, AgentRuntime runtime) {
        this.contractId = contractId;
        this.contractVersion = contractVersion;
        this.agent = agent;
        this.runtime = runtime;
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
        AgentRunState run = runtime.start(agent, invocation.input(), context);
        if (run.status() != AgentRunStatus.COMPLETED) {
            return ContractInvocationResult.failure(
                    run.errorCode() == null ? "AGENT_NOT_COMPLETED" : run.errorCode(),
                    run.errorMessage() == null ? "Agent run ended in status " + run.status() : run.errorMessage());
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("runId", run.runId());
        output.put("status", run.status().name());
        Map<String, Object> phaseOutputs = new LinkedHashMap<>();
        for (AgentPhaseState phase : run.phases().values()) {
            phaseOutputs.put(phase.phaseId(), phase.output());
        }
        output.put("phases", phaseOutputs);
        return ContractInvocationResult.success(output);
    }
}
