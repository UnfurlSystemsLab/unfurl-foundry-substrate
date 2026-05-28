package com.unfurl.foundry.substrate.offers;

import com.unfurl.dcp.contract.Binding;
import com.unfurl.dcp.contract.CompositionContract;
import com.unfurl.dcp.spi.ContractInvocableFactory;
import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.ports.AgentRuntime;
import com.unfurl.foundry.substrate.ports.ModelProvider;
import com.unfurl.foundry.substrate.ports.RagRetriever;
import com.unfurl.foundry.substrate.ports.ToolExecutor;
import com.unfurl.substrate.composition.ContractInvocable;
import com.unfurl.substrate.policy.ExecutionContext;

import java.util.Map;

/** DCP broker bridge for foundry-substrate's AI capability invocables. */
public final class FoundryContractInvocableFactory implements ContractInvocableFactory {
    private final AgentRuntime agentRuntime;
    private final Map<String, AgentDefinition> agentsByCapability;
    private final Map<String, ToolExecutor> toolsByCapability;
    private final Map<String, RagRetriever> retrieversByCapability;
    private final Map<String, ModelProvider> providersByCapability;

    public FoundryContractInvocableFactory(
            AgentRuntime agentRuntime,
            Map<String, AgentDefinition> agentsByCapability,
            Map<String, ToolExecutor> toolsByCapability,
            Map<String, RagRetriever> retrieversByCapability,
            Map<String, ModelProvider> providersByCapability
    ) {
        this.agentRuntime = agentRuntime;
        this.agentsByCapability = agentsByCapability == null ? Map.of() : Map.copyOf(agentsByCapability);
        this.toolsByCapability = toolsByCapability == null ? Map.of() : Map.copyOf(toolsByCapability);
        this.retrieversByCapability = retrieversByCapability == null ? Map.of() : Map.copyOf(retrieversByCapability);
        this.providersByCapability = providersByCapability == null ? Map.of() : Map.copyOf(providersByCapability);
    }

    @Override
    public ContractInvocable create(CompositionContract contract, Binding binding, ExecutionContext context) {
        String contractId = contract.contractId().toString();
        String contractVersion = contract.contractVersion();
        String capability = binding.providerCapability();
        return switch (capability) {
            case AiOffers.AGENT_RUN -> new AgentInvocation(contractId, contractVersion, requiredAgent(capability), requiredAgentRuntime());
            case AiOffers.TOOL_CALL -> new ToolInvocation(contractId, contractVersion, capability, requiredTool(capability));
            case AiOffers.RAG_SEARCH -> new RagInvocation(contractId, contractVersion, requiredRetriever(capability));
            case AiOffers.PROVIDER_CALL -> new ProviderInvocation(contractId, contractVersion, capability, requiredProvider(capability));
            default -> throw new IllegalArgumentException("Unsupported DCP AI capability: " + capability);
        };
    }

    private AgentRuntime requiredAgentRuntime() {
        if (agentRuntime == null) {
            throw new IllegalStateException("AgentRuntime is required for agent.run");
        }
        return agentRuntime;
    }

    private AgentDefinition requiredAgent(String capability) {
        AgentDefinition agent = agentsByCapability.get(capability);
        if (agent == null) {
            throw new IllegalArgumentException("No AgentDefinition registered for " + capability);
        }
        return agent;
    }

    private ToolExecutor requiredTool(String capability) {
        ToolExecutor tool = toolsByCapability.get(capability);
        if (tool == null) {
            throw new IllegalArgumentException("No ToolExecutor registered for " + capability);
        }
        return tool;
    }

    private RagRetriever requiredRetriever(String capability) {
        RagRetriever retriever = retrieversByCapability.get(capability);
        if (retriever == null) {
            throw new IllegalArgumentException("No RagRetriever registered for " + capability);
        }
        return retriever;
    }

    private ModelProvider requiredProvider(String capability) {
        ModelProvider provider = providersByCapability.get(capability);
        if (provider == null) {
            throw new IllegalArgumentException("No ModelProvider registered for " + capability);
        }
        return provider;
    }
}
