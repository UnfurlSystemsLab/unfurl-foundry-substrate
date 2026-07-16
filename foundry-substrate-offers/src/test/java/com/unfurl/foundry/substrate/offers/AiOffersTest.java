package com.unfurl.foundry.substrate.offers;

import com.unfurl.dcp.fault.FaultDeclaration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiOffersTest {

    @Test
    void exposesCanonicalAiOffers() {
        var offers = AiOffers.standardAiOffers("1.0.0");

        assertThat(offers).extracting(com.unfurl.dcp.claim.Offer::capability)
                .containsExactly(AiOffers.AGENT_RUN, AiOffers.TOOL_CALL,
                        AiOffers.RAG_SEARCH, AiOffers.PROVIDER_CALL, AiOffers.SKILL_INVOKE);
        assertThat(offers).allSatisfy(offer -> assertThat(offer.version()).isEqualTo("1.0.0"));
        assertThat(offers).filteredOn(com.unfurl.dcp.claim.Offer::metered)
                .allSatisfy(offer -> assertThat(offer.costImplications()).isNotBlank());
    }

    @Test
    void agentRunOfferDeclaresSimpleAndHarnessExecutionModes() {
        var offer = AiOffers.standardAiOffers("1.0.0").getFirst();

        assertThat(offer.capability()).isEqualTo(AiOffers.AGENT_RUN);
        assertThat(offer.offerInterface().details())
                .containsEntry(AiOffers.DETAIL_EXECUTION_MODES, List.of(AiOffers.MODE_SIMPLE, AiOffers.MODE_HARNESS))
                .containsEntry(AiOffers.DETAIL_DEFAULT_EXECUTION_MODE, AiOffers.MODE_SIMPLE)
                .containsKey(AiOffers.DETAIL_MODE_POLICIES);
    }

    @Test
    void exposesCanonicalFaultsForSelectedOffers() {
        var faults = AiOffers.faultPolicyFor(AiOffers.standardAiOffers("1.0.0"));

        assertThat(faults.emitted()).extracting(FaultDeclaration::code)
                .containsExactly(
                        "agent.run.failed",
                        "tool.call.failed",
                        "rag.search.degraded",
                        "provider.call.failed",
                        "skill.invoke.failed");
    }
}
