package com.unfurl.foundry.substrate.ports;

import com.unfurl.foundry.substrate.failure.FailureCategory;
import com.unfurl.foundry.substrate.failure.StructuredFailure;
import com.unfurl.substrate.policy.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies deterministic Chain-of-Responsibility ordering and monotone result policy. */
class ToolCallInterceptorChainTest {
    /** Before runs forward with normalized arguments and after runs in reverse. */
    @Test
    void appliesStableForwardAndReverseOrder() {
        List<String> order = new ArrayList<>();
        ToolCallInterceptor first = interceptor("first", order, "a");
        ToolCallInterceptor second = interceptor("second", order, "b");
        ToolCallInterceptorChain chain = new ToolCallInterceptorChain(List.of(first, second));
        ToolCallRequest request = new ToolCallRequest("1", "lookup", Map.of("seed", true), Map.of());

        ToolCallDecision decision = chain.before(request, ExecutionContext.empty());
        ToolCallResult result = chain.after(
                new ToolCallRequest("1", "lookup", decision.arguments(), decision.metadata()),
                ToolCallResult.success(Map.of("value", 1)), ExecutionContext.empty());

        assertThat(decision.arguments()).containsKeys("seed", "a", "b");
        assertThat(result.success()).isTrue();
        assertThat(order).containsExactly("before:first", "before:second", "after:second", "after:first");
    }

    /** The first denial short-circuits remaining policy and carries a structured failure. */
    @Test
    void shortCircuitsAtFirstDenial() {
        List<String> order = new ArrayList<>();
        ToolCallInterceptor deny = new ToolCallInterceptor() {
            /** Denies before any side effect. */
            @Override
            public ToolCallDecision before(ToolCallRequest request, ExecutionContext context) {
                order.add("deny");
                return ToolCallDecision.deny(request.arguments(), StructuredFailure.terminal(
                        "PREREQUISITE_MISSING", FailureCategory.AUTHORIZATION, "missing"), Map.of());
            }

            /** Leaves unreachable execution results unchanged. */
            @Override
            public ToolCallResult after(
                    ToolCallRequest request, ToolCallResult result, ExecutionContext context) {
                return result;
            }
        };
        ToolCallInterceptorChain chain = new ToolCallInterceptorChain(List.of(
                deny, interceptor("unreached", order, "x")));

        ToolCallDecision decision = chain.before(
                new ToolCallRequest("1", "lookup", Map.of(), Map.of()), ExecutionContext.empty());

        assertThat(decision.type()).isEqualTo(ToolCallDecisionType.DENY);
        assertThat(decision.failure().code()).isEqualTo("PREREQUISITE_MISSING");
        assertThat(order).containsExactly("deny");
    }

    /** After policy cannot restore redacted fields or convert a failure into success. */
    @Test
    void enforcesMonotoneAfterPolicy() {
        ToolCallInterceptor restoring = new ToolCallInterceptor() {
            /** Allows the call unchanged. */
            @Override
            public ToolCallDecision before(ToolCallRequest request, ExecutionContext context) {
                return ToolCallDecision.allow(request.arguments());
            }

            /** Illegally adds a new field to exercise chain enforcement. */
            @Override
            public ToolCallResult after(
                    ToolCallRequest request, ToolCallResult result, ExecutionContext context) {
                return result.withOutput(Map.of("kept", 1, "restored", "secret"));
            }
        };

        assertThatThrownBy(() -> new ToolCallInterceptorChain(List.of(restoring)).after(
                new ToolCallRequest("1", "lookup", Map.of(), Map.of()),
                ToolCallResult.success(Map.of("kept", 1)), ExecutionContext.empty()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot restore");
    }

    /** Fixture Factory: records ordering and appends one normalized argument. */
    private ToolCallInterceptor interceptor(
            String name, List<String> order, String argumentName) {
        return new ToolCallInterceptor() {
            /** Records forward ordering and returns a normalized argument map. */
            @Override
            public ToolCallDecision before(ToolCallRequest request, ExecutionContext context) {
                order.add("before:" + name);
                java.util.LinkedHashMap<String, Object> arguments = new java.util.LinkedHashMap<>(request.arguments());
                arguments.put(argumentName, true);
                return ToolCallDecision.allow(arguments);
            }

            /** Records reverse ordering without changing the result. */
            @Override
            public ToolCallResult after(
                    ToolCallRequest request, ToolCallResult result, ExecutionContext context) {
                order.add("after:" + name);
                return result;
            }
        };
    }
}
