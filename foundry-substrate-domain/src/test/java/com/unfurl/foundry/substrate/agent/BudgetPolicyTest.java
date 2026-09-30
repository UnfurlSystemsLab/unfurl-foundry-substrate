package com.unfurl.foundry.substrate.agent;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BudgetPolicyTest {

    /** Algebra contract: lower-of preserves every stricter ceiling and clamps default USD against an outer maximum. */
    @Test
    void composesLowerNonNullCeilingsWithoutInvalidDefaults() {
        BudgetPolicy outer = new BudgetPolicy(null, new BigDecimal("3"), 50L, null, 90L, Map.of("outer", true));
        BudgetPolicy inner = new BudgetPolicy(new BigDecimal("5"), new BigDecimal("10"), 100L, 40L, 120L, Map.of("inner", true));
        BudgetPolicy result = BudgetPolicy.lowerOf(outer, inner);
        assertThat(result.defaultBudgetUsd()).isEqualByComparingTo("3");
        assertThat(result.maxBudgetUsd()).isEqualByComparingTo("3");
        assertThat(result.maxPromptTokens()).isEqualTo(50L);
        assertThat(result.maxCompletionTokens()).isEqualTo(40L);
        assertThat(result.maxTotalTokens()).isEqualTo(90L);
        assertThat(result.metadata()).containsEntry("outer", true).containsEntry("inner", true);
        assertThat(BudgetPolicy.lowerOf(null, inner)).isEqualTo(inner);
        assertThat(BudgetPolicy.lowerOf(outer, new BudgetPolicy(null, BigDecimal.ZERO, null, null, null, Map.of()))
                .maxBudgetUsd()).isZero();
    }

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
