package com.unfurl.foundry.substrate.guardrail;

import com.unfurl.foundry.substrate.agent.BudgetPolicy;
import com.unfurl.substrate.policy.ExecutionContext;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

/** Conformance: budget projection is monotone and malformed host limits cannot become unlimited. */
class CostGuardrailContextTest {
    /** Checks agent-owned limits do not erase stricter inherited spend or token ceilings. */
    @Test void retainsEveryLowerCallerCeiling() {
        var caller = new BudgetPolicy(null, new BigDecimal("2"), 10L, 20L, 30L, Map.of());
        var declared = new BudgetPolicy(null, new BigDecimal("5"), 100L, 200L, 300L, Map.of());
        var context = new ExecutionContext("tenant", "user", List.of(), List.of(), null, null, Map.of(),
                Map.of(CostGuardrailContext.AGENT_BUDGET_POLICY, caller));
        assertThat(CostGuardrailContext.agentBudgetPolicy(CostGuardrailContext.withAgentBudgetPolicy(context, declared)))
                .contains(caller);
    }

    /** Checks zero survives projection while an absent inherited policy retains the declared agent ceiling. */
    @Test void preservesExhaustedAndAbsentLimits() {
        var exhausted = new BudgetPolicy(null, BigDecimal.ZERO, 0L, 0L, 0L, Map.of());
        var context = new ExecutionContext("tenant", "user", List.of(), List.of(), null, null, Map.of(),
                Map.of(CostGuardrailContext.AGENT_BUDGET_POLICY, exhausted));
        assertThat(CostGuardrailContext.agentBudgetPolicy(CostGuardrailContext.withAgentBudgetPolicy(context, BudgetPolicy.none())))
                .contains(exhausted);
        assertThat(CostGuardrailContext.agentBudgetPolicy(CostGuardrailContext.withAgentBudgetPolicy(ExecutionContext.empty(), exhausted)))
                .contains(exhausted);
    }

    /** Checks malformed known metadata is a rejection rather than a permissive policy replacement. */
    @Test void rejectsMalformedInheritedPolicy() {
        var context = new ExecutionContext("tenant", "user", List.of(), List.of(), null, null, Map.of(),
                Map.of(CostGuardrailContext.AGENT_BUDGET_POLICY, Map.of("maxTotalTokens", 1)));
        assertThatThrownBy(() -> CostGuardrailContext.withAgentBudgetPolicy(context, BudgetPolicy.none()))
                .hasMessageContaining("must be a BudgetPolicy");
    }
}
