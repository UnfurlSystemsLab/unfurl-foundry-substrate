package com.unfurl.foundry.substrate.offers;

import org.junit.jupiter.api.Test;

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
}
