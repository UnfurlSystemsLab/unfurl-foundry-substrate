package com.unfurl.foundry.substrate.engine;

import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.agent.BudgetPolicy;
import com.unfurl.foundry.substrate.delegation.AgentDelegationRequest;
import com.unfurl.foundry.substrate.ports.AgentRuntime;
import com.unfurl.foundry.substrate.runstate.AgentRunState;
import com.unfurl.foundry.substrate.runstate.AgentRunStatus;
import com.unfurl.foundry.substrate.runstate.CostAccounting;
import com.unfurl.substrate.policy.ExecutionContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Contract tests for least-authority projection and lower-of delegation policy. */
class SequentialAgentDelegateTest {
    /** Verifies a child receives only declared input, permission intersection, and stricter budgets. */
    @Test
    void projectsOnlyDeclaredInputAndComposesAuthority() {
        AtomicReference<Map<String, Object>> capturedInput = new AtomicReference<>();
        AtomicReference<ExecutionContext> capturedContext = new AtomicReference<>();
        BudgetPolicy childBudget = new BudgetPolicy(new BigDecimal("8"), new BigDecimal("10"),
                80L, 40L, 100L, Map.of());
        AgentDefinition child = new AgentDefinition("child", "1", Map.of(), List.of(), List.of(),
                Map.of(), null, List.of(), childBudget);
        AgentRuntime runtime = capturingRuntime(capturedInput, capturedContext);
        SequentialAgentDelegate delegate = new SequentialAgentDelegate(
                (id, version, context) -> id.equals("child") && version.equals("1")
                        ? java.util.Optional.of(child) : java.util.Optional.empty(), runtime);
        BudgetPolicy outer = new BudgetPolicy(new BigDecimal("5"), new BigDecimal("7"),
                50L, 60L, 90L, Map.of());
        AgentDelegationRequest request = new AgentDelegationRequest("child@1", "analyze",
                Map.of("case", "A"), List.of("source:1"), "schema:answer", outer,
                List.of("read", "write"), Map.of());
        ExecutionContext caller = new ExecutionContext("tenant", "user", List.of("role"),
                List.of("read", "admin"), "correlation", "request", Map.of(), Map.of("secret", "not inherited"));

        var result = delegate.invoke(request, caller);

        assertThat(result.success()).isTrue();
        assertThat(result.terminal().output()).isEmpty();
        assertThat(capturedInput.get()).containsOnlyKeys("objective", "context", "sources", "expectedOutputSchemaRef");
        assertThat(capturedContext.get().permissions()).containsExactly("read");
        assertThat(capturedContext.get().metadata()).doesNotContainKey("secret");
        assertThat(result.effectiveBudget().maxTotalTokens()).isEqualTo(90L);
        assertThat(result.effectiveBudget().maxPromptTokens()).isEqualTo(50L);
        assertThat(result.effectiveBudget().maxCompletionTokens()).isEqualTo(40L);
        assertThat(result.effectiveBudget().maxBudgetUsd()).isEqualByComparingTo("7");
    }

    /** Fixture Adapter: captures boundary values and returns a valid intentionally empty success. */
    private AgentRuntime capturingRuntime(AtomicReference<Map<String, Object>> input,
                                          AtomicReference<ExecutionContext> context) {
        return new AgentRuntime() {
            /** Captures delegated input and context before returning a completed child run. */
            @Override public AgentRunState start(AgentDefinition agent, Map<String, Object> value,
                                                 ExecutionContext executionContext) {
                input.set(value); context.set(executionContext);
                return new AgentRunState(executionContext.tenantId(), "run-1", agent.id(), agent.version(),
                        AgentRunStatus.COMPLETED, value, Map.of(), CostAccounting.empty(Map.of()),
                        null, null, Instant.EPOCH, Instant.EPOCH);
            }
            /** Unsupported fixture path: this delegate test never resumes children. */
            @Override public AgentRunState resume(String runId, Map<String, Object> signal,
                                                  ExecutionContext executionContext) {
                throw new UnsupportedOperationException();
            }
            /** Unsupported fixture path: this delegate test never cancels children. */
            @Override public AgentRunState cancel(String runId, ExecutionContext executionContext) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
