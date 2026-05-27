package com.unfurl.foundry.substrate.guardrail;

import com.unfurl.foundry.substrate.agent.BudgetPolicy;
import com.unfurl.foundry.substrate.runstate.CostAccounting;
import com.unfurl.substrate.policy.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetPolicyCostGuardrailTest {
    private final BudgetPolicyCostGuardrail guardrail = new BudgetPolicyCostGuardrail();

    @Test
    void deniesWhenTokenCeilingIsReached() {
        ExecutionContext context = CostGuardrailContext.withAgentBudgetPolicy(ExecutionContext.empty(),
                new BudgetPolicy(null, null, null, null, 10L, Map.of()));
        CostAccounting accounting = CostAccounting.empty(Map.of()).add(4, 6, "model", "provider");

        GuardrailDecision decision = guardrail.check(accounting, context);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("Total token budget");
    }

    @Test
    void usesLowerOfAgentAndOuterUsdBudget() {
        ExecutionContext context = withOuterBudget(new BudgetPolicy(new BigDecimal("10.00"), null,
                null, null, null, Map.of()), new BigDecimal("5.00"));
        CostAccounting accounting = CostAccounting.empty(Map.of())
                .add(1, 1, "model", "provider", new BigDecimal("5.00"));

        GuardrailDecision decision = guardrail.check(accounting, context);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("USD budget");
    }

    @Test
    void allowsWhenNoCeilingIsReached() {
        ExecutionContext context = withOuterBudget(new BudgetPolicy(new BigDecimal("10.00"), null,
                null, null, 100L, Map.of()), new BigDecimal("20.00"));
        CostAccounting accounting = CostAccounting.empty(Map.of())
                .add(1, 1, "model", "provider", new BigDecimal("1.00"));

        assertThat(guardrail.check(accounting, context).allowed()).isTrue();
    }

    private ExecutionContext withOuterBudget(BudgetPolicy policy, BigDecimal outerBudget) {
        ExecutionContext context = CostGuardrailContext.withAgentBudgetPolicy(ExecutionContext.empty(), policy);
        Map<String, Object> metadata = new java.util.LinkedHashMap<>(context.metadata());
        metadata.put(CostGuardrailContext.OUTER_BUDGET_REMAINING_USD, outerBudget);
        return new ExecutionContext(context.tenantId(), context.userId(), List.of(), List.of(),
                context.correlationId(), context.requestId(), context.traceContext(), metadata);
    }
}
