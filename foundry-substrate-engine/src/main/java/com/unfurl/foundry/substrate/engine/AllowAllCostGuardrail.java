package com.unfurl.foundry.substrate.engine;

import com.unfurl.foundry.substrate.guardrail.CostGuardrail;
import com.unfurl.foundry.substrate.guardrail.GuardrailDecision;
import com.unfurl.foundry.substrate.runstate.CostAccounting;
import com.unfurl.substrate.policy.ExecutionContext;

/** Default guardrail that always allows. Real budgets/quotas live in unfurl-foundry. */
/**
 * class for the Foundry AI substrate surface; documents the AllowAllCostGuardrail contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public final class AllowAllCostGuardrail implements CostGuardrail {
/**
 * Implements the check helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    @Override
    public GuardrailDecision check(CostAccounting accounting, ExecutionContext context) {
        return GuardrailDecision.allow();
    }
}
