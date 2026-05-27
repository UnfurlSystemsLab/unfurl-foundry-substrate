package com.unfurl.foundry.substrate.agent;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BudgetPolicyTest {

    @Test
    void defaultsToNoCeilings() {
        BudgetPolicy policy = BudgetPolicy.none();

        assertThat(policy.hasUsdCeiling()).isFalse();
        assertThat(policy.hasTokenCeiling()).isFalse();
        assertThat(policy.metadata()).isEmpty();
    }

    @Test
    void identifiesUsdAndTokenCeilings() {
        BudgetPolicy policy = new BudgetPolicy(
                new BigDecimal("5.00"),
                new BigDecimal("10.00"),
                100L,
                200L,
                300L,
                Map.of("tier", "test")
        );

        assertThat(policy.hasUsdCeiling()).isTrue();
        assertThat(policy.hasTokenCeiling()).isTrue();
    }

    @Test
    void rejectsInvalidCeilings() {
        assertThatThrownBy(() -> new BudgetPolicy(new BigDecimal("-1.00"), null, null, null, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defaultBudgetUsd");
        assertThatThrownBy(() -> new BudgetPolicy(new BigDecimal("11.00"), new BigDecimal("10.00"), null, null, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defaultBudgetUsd must be <= maxBudgetUsd");
        assertThatThrownBy(() -> new BudgetPolicy(null, null, -1L, null, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxPromptTokens");
    }
}
