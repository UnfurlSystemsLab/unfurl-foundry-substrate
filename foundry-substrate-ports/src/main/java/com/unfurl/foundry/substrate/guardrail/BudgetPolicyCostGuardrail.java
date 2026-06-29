package com.unfurl.foundry.substrate.guardrail;

import com.unfurl.foundry.substrate.agent.BudgetPolicy;
import com.unfurl.foundry.substrate.runstate.CostAccounting;
import com.unfurl.substrate.policy.ExecutionContext;

import java.math.BigDecimal;

/**
 * Pure reference guardrail that applies the resolved agent budget policy plus
 * an optional outer workflow-run budget envelope.
 */
public final class BudgetPolicyCostGuardrail implements CostGuardrail {
/**
 * Implements the check helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    @Override
    public GuardrailDecision check(CostAccounting accounting, ExecutionContext context) {
        CostAccounting cost = accounting == null ? CostAccounting.empty(null) : accounting;
        BudgetPolicy policy = CostGuardrailContext.agentBudgetPolicy(context).orElse(BudgetPolicy.none());

        GuardrailDecision tokenDecision = checkTokenCeilings(cost, policy);
        if (!tokenDecision.allowed()) {
            return tokenDecision;
        }

        BigDecimal effectiveUsd = effectiveUsdCeiling(policy, context);
        if (effectiveUsd != null && cost.estimatedCostUsd().compareTo(effectiveUsd) >= 0) {
            return GuardrailDecision.deny("USD budget exceeded");
        }
        return GuardrailDecision.allow();
    }

/**
 * Implements the checkTokenCeilings helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private GuardrailDecision checkTokenCeilings(CostAccounting cost, BudgetPolicy policy) {
        if (policy.maxPromptTokens() != null && cost.promptTokens() >= policy.maxPromptTokens()) {
            return GuardrailDecision.deny("Prompt token budget exceeded");
        }
        if (policy.maxCompletionTokens() != null && cost.completionTokens() >= policy.maxCompletionTokens()) {
            return GuardrailDecision.deny("Completion token budget exceeded");
        }
        if (policy.maxTotalTokens() != null && cost.totalTokens() >= policy.maxTotalTokens()) {
            return GuardrailDecision.deny("Total token budget exceeded");
        }
        return GuardrailDecision.allow();
    }

/**
 * Implements the effectiveUsdCeiling helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private BigDecimal effectiveUsdCeiling(BudgetPolicy policy, ExecutionContext context) {
        BigDecimal agentCeiling = policy.defaultBudgetUsd() != null ? policy.defaultBudgetUsd() : policy.maxBudgetUsd();
        BigDecimal outer = CostGuardrailContext.outerBudgetRemainingUsd(context).orElse(null);
        if (agentCeiling == null) {
            return outer;
        }
        if (outer == null) {
            return agentCeiling;
        }
        return agentCeiling.min(outer);
    }
}
