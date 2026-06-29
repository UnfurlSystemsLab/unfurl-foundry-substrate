package com.unfurl.foundry.substrate.offers;

import com.unfurl.dcp.claim.ConsumerAccess;
import com.unfurl.dcp.claim.InterfaceKind;
import com.unfurl.dcp.claim.Offer;
import com.unfurl.dcp.claim.OfferInterface;
import com.unfurl.dcp.claim.Stability;

import java.util.List;
import java.util.Map;

/** Canonical DCP offers for the AI capabilities exposed by foundry-substrate. */
/**
 * class for the Foundry AI substrate surface; documents the AiOffers contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public final class AiOffers {
    public static final String AGENT_RUN = "agent.run";
    public static final String TOOL_CALL = "tool.call";
    public static final String RAG_SEARCH = "rag.search";
    public static final String PROVIDER_CALL = "provider.call";
    public static final String SKILL_INVOKE = "skill.invoke";

/**
 * Constructs AiOffers with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    private AiOffers() {
    }

/**
 * Factory method: creates the standardAiOffers result while keeping caller-facing defaults and validation in one place.
 */
    public static List<Offer> standardAiOffers(String version) {
        return List.of(
                metered(AGENT_RUN, "Run an agent", "start", version, "AgentInput", "AgentOutput", "tokens"),
                unmetered(TOOL_CALL, "Call an allowed tool", "execute", version, "ToolCallRequest", "ToolCallResult"),
                metered(RAG_SEARCH, "Retrieve grounded context", "retrieve", version, "RagQuery", "RagResult", "tokens"),
                metered(PROVIDER_CALL, "Call a configured model provider", "complete", version, "ModelRequest", "ModelResponse", "tokens"),
                metered(SKILL_INVOKE, "Resolve and invoke a governed skill", "invoke", version,
                        "SkillInvocationRequest", "SkillInvocationResult", "tokens")
        );
    }

/**
 * Implements the metered helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private static Offer metered(String capability, String description, String operation, String version, String input, String output, String unit) {
        return offer(capability, description, operation, version, input, output, true, "metered=true; unit=" + unit);
    }

/**
 * Implements the unmetered helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private static Offer unmetered(String capability, String description, String operation, String version, String input, String output) {
        return offer(capability, description, operation, version, input, output, false, null);
    }

/**
 * Factory method: creates the offer result while keeping caller-facing defaults and validation in one place.
 */
    private static Offer offer(String capability, String description, String operation, String version, String input, String output, boolean metered, String costImplications) {
        return new Offer(
                capability,
                description,
                ConsumerAccess.ANY,
                new OfferInterface(InterfaceKind.IN_PROCESS, Map.of("operation", operation, "inputShape", input, "outputShape", output)),
                Stability.STABLE,
                version,
                metered,
                costImplications);
    }
}
