package com.unfurl.foundry.substrate.offers;

import com.unfurl.dcp.claim.ConsumerAccess;
import com.unfurl.dcp.claim.InterfaceKind;
import com.unfurl.dcp.claim.Offer;
import com.unfurl.dcp.claim.OfferInterface;
import com.unfurl.dcp.claim.Stability;

import java.util.List;
import java.util.Map;

/** Canonical DCP offers for the AI capabilities exposed by foundry-substrate. */
public final class AiOffers {
    public static final String AGENT_RUN = "agent.run";
    public static final String TOOL_CALL = "tool.call";
    public static final String RAG_SEARCH = "rag.search";
    public static final String PROVIDER_CALL = "provider.call";
    public static final String SKILL_INVOKE = "skill.invoke";

    private AiOffers() {
    }

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

    private static Offer metered(String capability, String description, String operation, String version, String input, String output, String unit) {
        return offer(capability, description, operation, version, input, output, true, "metered=true; unit=" + unit);
    }

    private static Offer unmetered(String capability, String description, String operation, String version, String input, String output) {
        return offer(capability, description, operation, version, input, output, false, null);
    }

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
