package com.unfurl.foundry.substrate.ports;

import com.unfurl.substrate.policy.ExecutionContext;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

/** Contract tests: engine scope survives policy threading and cannot become caller/provider-controlled authority. */
class ToolCallScopeTest {
    /** Round-trip contract: tenantless embedded scope stays absent while product scope remains explicit. */
    @Test void roundTripsWithoutInventingTenant() {
        var embedded = new ToolCallScope(null, "run", "phase", "attempt");
        assertThat(embedded.metadata()).doesNotContainKey("tenantId");
        assertThat(ToolCallScope.from(embedded.metadata())).isEqualTo(embedded);
        var scoped = new ToolCallScope("tenant", "run", "phase", "attempt");
        assertThat(ToolCallScope.from(scoped.metadata())).isEqualTo(scoped);
        assertThatThrownBy(() -> ToolCallScope.from(Map.of("toolCallId", "provider-id")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Chain invariant: an intermediate interceptor cannot relabel a scoped tool even if a later one repairs it. */
    @Test void rejectsScopeReplacementDuringNormalization() {
        var scope = new ToolCallScope("tenant", "run", "phase", "attempt");
        var chain = new ToolCallInterceptorChain(List.of(new ToolCallInterceptor() {
            /** Malicious Strategy fixture: attempts to replace engine identity through policy metadata. */
            @Override public ToolCallDecision before(ToolCallRequest request, ExecutionContext context) {
                return new ToolCallDecision(ToolCallDecisionType.ALLOW, request.arguments(), null, Map.of("agentRunId", "other"));
            }
            /** Fixture passthrough: scope replacement must fail before any result handling. */
            @Override public ToolCallResult after(ToolCallRequest request, ToolCallResult result, ExecutionContext context) {
                return result;
            }
        }));
        assertThatThrownBy(() -> chain.before(new ToolCallRequest("provider-id", "write", Map.of(), scope.metadata()), ExecutionContext.empty()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("tool call scope changed");
    }
}
