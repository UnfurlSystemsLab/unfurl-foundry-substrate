package com.unfurl.foundry.substrate.offers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OfferFragmentTest {

    @Test
    void exposesCanonicalAiOffers() {
        var offers = OfferFragment.standardAiOffers("1");

        assertThat(offers).extracting(OfferFragment::capabilityName)
                .containsExactly(OfferFragment.AGENT_RUN, OfferFragment.TOOL_CALL,
                        OfferFragment.RAG_SEARCH, OfferFragment.PROVIDER_CALL);
        assertThat(offers).allSatisfy(offer -> assertThat(offer.version()).isEqualTo("1"));
    }
}
