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
/**
 * class for the Foundry AI substrate surface; documents the FoundryContractInvocableFactory contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public final class FoundryContractInvocableFactory implements ContractInvocableFactory {
    private final AgentRuntime agentRuntime;
    private final Map<String, AgentDefinition> agentsByCapability;
    private final Map<String, ToolExecutor> toolsByCapability;
    private final Map<String, RagRetriever> retrieversByCapability;
    private final Map<String, ModelProvider> providersByCapability;

/**
 * Constructs FoundryContractInvocableFactory with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
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

/**
 * Implements the create helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
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

/**
 * Implements the requiredAgentRuntime helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private AgentRuntime requiredAgentRuntime() {
        if (agentRuntime == null) {
            throw new IllegalStateException("AgentRuntime is required for agent.run");
        }
        return agentRuntime;
    }

/**
 * Implements the requiredAgent helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private AgentDefinition requiredAgent(String capability) {
        AgentDefinition agent = agentsByCapability.get(capability);
        if (agent == null) {
            throw new IllegalArgumentException("No AgentDefinition registered for " + capability);
        }
        return agent;
    }

/**
 * Implements the requiredTool helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private ToolExecutor requiredTool(String capability) {
        ToolExecutor tool = toolsByCapability.get(capability);
        if (tool == null) {
            throw new IllegalArgumentException("No ToolExecutor registered for " + capability);
        }
        return tool;
    }

/**
 * Implements the requiredRetriever helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private RagRetriever requiredRetriever(String capability) {
        RagRetriever retriever = retrieversByCapability.get(capability);
        if (retriever == null) {
            throw new IllegalArgumentException("No RagRetriever registered for " + capability);
        }
        return retriever;
    }

/**
 * Implements the requiredProvider helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private ModelProvider requiredProvider(String capability) {
        ModelProvider provider = providersByCapability.get(capability);
        if (provider == null) {
            throw new IllegalArgumentException("No ModelProvider registered for " + capability);
        }
        return provider;
    }
}
