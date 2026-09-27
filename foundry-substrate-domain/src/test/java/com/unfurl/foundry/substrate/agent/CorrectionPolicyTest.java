package com.unfurl.foundry.substrate.agent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies correction policy always provides finite retry and time bounds. */
class CorrectionPolicyTest {
    /** No-correction policy is valid and defaults to deterministic failure on validation rejection. */
    @Test
    void supportsValidationWithoutCorrection() {
        assertThat(CorrectionPolicy.none().maxAttempts()).isZero();
        assertThat(CorrectionPolicy.none().exhaustionAction()).isEqualTo(CorrectionExhaustionAction.FAIL);
    }

    /** Enabled correction cannot omit its wall-clock duration bound. */
    @Test
    void rejectsAttemptBoundWithoutDurationBound() {
        assertThatThrownBy(() -> new CorrectionPolicy(
                1, 0, CorrectionExhaustionAction.FAIL, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxDurationMillis");
    }
}
