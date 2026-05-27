package com.unfurl.foundry.substrate.engine;

import com.unfurl.foundry.substrate.guardrail.CostGuardrail;
import com.unfurl.foundry.substrate.guardrail.GuardrailDecision;
import com.unfurl.foundry.substrate.runstate.CostAccounting;
import com.unfurl.substrate.policy.ExecutionContext;

/** Default guardrail that always allows. Real budgets/quotas live in unfurl-foundry. */
public final class AllowAllCostGuardrail implements CostGuardrail {
    @Override
    public GuardrailDecision check(CostAccounting accounting, ExecutionContext context) {
        return GuardrailDecision.allow();
    }
}
