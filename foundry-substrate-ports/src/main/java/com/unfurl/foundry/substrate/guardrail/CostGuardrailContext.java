package com.unfurl.foundry.substrate.guardrail;

import com.unfurl.foundry.substrate.agent.BudgetPolicy;
import com.unfurl.substrate.policy.ExecutionContext;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Shared metadata keys for passing budget envelopes through neutral execution context. */
public final class CostGuardrailContext {
    public static final String AGENT_BUDGET_POLICY = "agentBudgetPolicy";
    public static final String OUTER_BUDGET_REMAINING_USD = "outerBudgetRemainingUsd";

    private CostGuardrailContext() {
    }

    public static ExecutionContext withAgentBudgetPolicy(ExecutionContext context, BudgetPolicy policy) {
        ExecutionContext base = context == null ? ExecutionContext.empty() : context;
        Map<String, Object> metadata = new LinkedHashMap<>(base.metadata());
        metadata.put(AGENT_BUDGET_POLICY, policy == null ? BudgetPolicy.none() : policy);
        return new ExecutionContext(base.tenantId(), base.userId(), base.roles(), base.permissions(),
                base.correlationId(), base.requestId(), base.traceContext(), metadata);
    }

    public static Optional<BudgetPolicy> agentBudgetPolicy(ExecutionContext context) {
        if (context == null) {
            return Optional.empty();
        }
        Object value = context.metadata().get(AGENT_BUDGET_POLICY);
        return value instanceof BudgetPolicy policy ? Optional.of(policy) : Optional.empty();
    }

    public static Optional<BigDecimal> outerBudgetRemainingUsd(ExecutionContext context) {
        if (context == null) {
            return Optional.empty();
        }
        Object value = context.metadata().get(OUTER_BUDGET_REMAINING_USD);
        if (value instanceof BigDecimal decimal) {
            return Optional.of(decimal);
        }
        if (value instanceof Number number) {
            return Optional.of(BigDecimal.valueOf(number.doubleValue()));
        }
        if (value instanceof String string && !string.isBlank()) {
            return Optional.of(new BigDecimal(string));
        }
        return Optional.empty();
    }
}
