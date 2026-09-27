package com.unfurl.foundry.substrate.agent;

import java.util.Map;

/** Value Object: finite attempt and duration bounds for validation-driven model correction. */
public record CorrectionPolicy(
        int maxAttempts,
        long maxDurationMillis,
        CorrectionExhaustionAction exhaustionAction,
        Map<String, Object> metadata
) {
    /** Validates finite bounds and freezes extension metadata. */
    public CorrectionPolicy {
        if (maxAttempts < 0) {
            throw new IllegalArgumentException("maxAttempts must be >= 0");
        }
        if (maxDurationMillis < 0) {
            throw new IllegalArgumentException("maxDurationMillis must be >= 0");
        }
        if (maxAttempts > 0 && maxDurationMillis == 0) {
            throw new IllegalArgumentException(
                    "maxDurationMillis must be > 0 when correction attempts are enabled");
        }
        exhaustionAction = exhaustionAction == null ? CorrectionExhaustionAction.FAIL : exhaustionAction;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** Null Object: validates once and performs no correction model calls. */
    public static CorrectionPolicy none() {
        return new CorrectionPolicy(0, 0, CorrectionExhaustionAction.FAIL, Map.of());
    }
}
