package com.unfurl.foundry.substrate.offers;

import java.util.List;
import java.util.Map;

/**
 * PLACEHOLDER for a DCP claim offer fragment.
 *
 * <p>Per the LLD, the canonical claim/offer schema is owned by {@code unfurl-dcp}; this
 * record stands in for the AI-specific {@code offers[*]} fragment until that artifact
 * exists. It describes one AI capability a component exposes: the operation name, the
 * input/output shape references, the offered version, and (required when metered) the
 * cost implications a reporting layer attributes spend against.
 *
 * <p>When {@code unfurl-dcp} publishes, replace this with the real claim offer type and
 * add the dependency in this module's POM.
 */
public record OfferFragment(
        String capabilityName,
        String operation,
        String version,
        String inputShapeRef,
        String outputShapeRef,
        Map<String, Object> costImplications
) {
    public OfferFragment {
        costImplications = costImplications == null ? Map.of() : Map.copyOf(costImplications);
    }

    public static final String AGENT_RUN = "agent.run";
    public static final String TOOL_CALL = "tool.call";
    public static final String RAG_SEARCH = "rag.search";
    public static final String PROVIDER_CALL = "provider.call";

    /** The standard AI capability offers a foundry component publishes in its claim. */
    public static List<OfferFragment> standardAiOffers(String version) {
        return List.of(
                new OfferFragment(AGENT_RUN, "start", version, "AgentInput", "AgentOutput", Map.of("metered", true, "unit", "tokens")),
                new OfferFragment(TOOL_CALL, "execute", version, "ToolCallRequest", "ToolCallResult", Map.of("metered", false)),
                new OfferFragment(RAG_SEARCH, "retrieve", version, "RagQuery", "RagResult", Map.of("metered", true, "unit", "tokens")),
                new OfferFragment(PROVIDER_CALL, "complete", version, "ModelRequest", "ModelResponse", Map.of("metered", true, "unit", "tokens"))
        );
    }
}
