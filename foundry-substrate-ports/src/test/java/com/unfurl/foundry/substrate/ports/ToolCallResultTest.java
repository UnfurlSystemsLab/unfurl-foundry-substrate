package com.unfurl.foundry.substrate.ports;

import com.unfurl.foundry.substrate.failure.FailureCategory;
import com.unfurl.foundry.substrate.failure.StructuredFailure;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCallResultTest {
    /**
     * Category fixture: exposes every stable failure category to the parameterized contract test.
     */
    private static Stream<FailureCategory> failureCategories() {
        return Stream.of(FailureCategory.values());
    }

    /**
     * Verifies an empty result remains an explicit success without a synthesized not-found failure.
     */
    @Test
    void preservesSuccessfulEmptyOutput() {
        ToolCallResult result = ToolCallResult.emptySuccess();

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEmpty();
        assertThat(result.failure()).isNull();
    }

    /**
     * Verifies the former constructor remains source-compatible and promotes its aliases.
     */
    @Test
    void promotesLegacyFailureConstructor() {
        ToolCallResult result = new ToolCallResult(false, Map.of(), "LEGACY", "legacy failure");

        assertThat(result.failure().code()).isEqualTo("LEGACY");
        assertThat(result.failure().category()).isEqualTo(FailureCategory.INTERNAL);
        assertThat(result.errorCode()).isEqualTo("LEGACY");
    }

    /**
     * Verifies canonical structured failures retain partial output and retry metadata.
     */
    @Test
    void retainsStructuredFailureDetails() {
        StructuredFailure failure = new StructuredFailure(
                "RATE_LIMITED",
                FailureCategory.RATE_LIMIT,
                true,
                250L,
                "retry later",
                Map.of("accepted", 2),
                Map.of("limit", 10),
                Map.of("provider", "catalog"));

        ToolCallResult result = ToolCallResult.failure(failure);

        assertThat(result.output()).containsEntry("accepted", 2);
        assertThat(result.failure()).isEqualTo(failure);
        assertThat(result.failure().retryable()).isTrue();
    }

    /**
     * Verifies every public category survives promotion through the ToolCallResult boundary.
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("failureCategories")
    void supportsEveryFailureCategory(FailureCategory category) {
        ToolCallResult result = ToolCallResult.failure(
                StructuredFailure.terminal("CATEGORY_TEST", category, "failed"));

        assertThat(result.failure().category()).isEqualTo(category);
    }
}
