package com.unfurl.foundry.substrate.guardrail;

import com.unfurl.foundry.substrate.runstate.CostAccounting;
import com.unfurl.substrate.policy.ExecutionContext;

/**
 * A per-call budget decision the runner consults before a model call. A decision, not
 * enforcement state: budgets/quotas/rate-limits are persisted and enforced in
 * {@code unfurl-foundry}, never here.
 */
public interface CostGuardrail {
    GuardrailDecision check(CostAccounting accounting, ExecutionContext context);
}
