package com.unfurl.foundry.substrate.engine;

import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.agent.BudgetPolicy;
import com.unfurl.foundry.substrate.delegation.AgentDelegationRequest;
import com.unfurl.foundry.substrate.delegation.AgentDelegationResult;
import com.unfurl.foundry.substrate.failure.FailureCategory;
import com.unfurl.foundry.substrate.failure.StructuredFailure;
import com.unfurl.foundry.substrate.ports.AgentDefinitionResolver;
import com.unfurl.foundry.substrate.ports.AgentDelegate;
import com.unfurl.foundry.substrate.ports.AgentRuntime;
import com.unfurl.foundry.substrate.runstate.AgentPhaseState;
import com.unfurl.foundry.substrate.runstate.AgentRunState;
import com.unfurl.foundry.substrate.runstate.CostAccounting;
import com.unfurl.foundry.substrate.terminal.AgentTerminalEnvelope;
import com.unfurl.foundry.substrate.terminal.AgentTerminalStatus;
import com.unfurl.foundry.substrate.terminal.TerminalEnvelopeNormalizer;
import com.unfurl.substrate.policy.ExecutionContext;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Strategy: invokes pinned child agents sequentially with explicit projection, intersected
 * permissions, and the lower ceiling from caller and child budgets.
 */
public final class SequentialAgentDelegate implements AgentDelegate {
    private final AgentDefinitionResolver resolver;
    private final AgentRuntime runtime;
    private final TerminalEnvelopeNormalizer terminalNormalizer;

    /** Constructor: binds the definition repository and ordinary agent runtime used for children. */
    public SequentialAgentDelegate(AgentDefinitionResolver resolver, AgentRuntime runtime) {
        this(resolver, runtime, new TerminalEnvelopeNormalizer());
    }

    /** Constructor: permits an explicit terminal normalization strategy for deterministic hosts/tests. */
    public SequentialAgentDelegate(AgentDefinitionResolver resolver, AgentRuntime runtime,
                                   TerminalEnvelopeNormalizer terminalNormalizer) {
        this.resolver = Objects.requireNonNull(resolver, "resolver is required");
        this.runtime = Objects.requireNonNull(runtime, "runtime is required");
        this.terminalNormalizer = Objects.requireNonNull(terminalNormalizer, "terminalNormalizer is required");
    }

    /**
     * Invokes one child while ensuring its input contains no parent transcript, sibling output,
     * undeclared source, budget, permission, or execution metadata.
     */
    @Override
    public AgentDelegationResult invoke(AgentDelegationRequest request, ExecutionContext context) {
        Objects.requireNonNull(request, "request is required");
        ExecutionContext caller = context == null ? ExecutionContext.empty() : context;
        String[] pinned = request.agentRef().split("@", 2);
        AgentDefinition child = resolver.resolve(pinned[0], pinned[1], caller).orElse(null);
        if (child == null) {
            return failure("AGENT_NOT_FOUND", FailureCategory.NOT_FOUND, false,
                    "Pinned child agent was not found", Map.of(), BudgetPolicy.none(), List.of(), request);
        }

        BudgetPolicy effectiveBudget = lowerOf(request.budgetEnvelope(), child.budgetPolicy());
        List<String> effectivePermissions = caller.permissions().stream()
                .filter(request.permissionScope()::contains).distinct().toList();
        Map<String, Object> childInput = projectedInput(request);
        Map<String, Object> childMetadata = new LinkedHashMap<>();
        childMetadata.put("agentBudgetPolicy", effectiveBudget);
        if (caller.requestId() != null) childMetadata.put("delegatedByRequestId", caller.requestId());
        ExecutionContext childContext = new ExecutionContext(
                caller.tenantId(), caller.userId(), caller.roles(), effectivePermissions,
                caller.correlationId(), caller.requestId(), caller.traceContext(), childMetadata);

        AgentRunState run;
        try {
            run = runtime.start(child, childInput, childContext);
        } catch (RuntimeException exception) {
            return failure("CHILD_RUNTIME_FAILED", FailureCategory.TRANSIENT, true,
                    "Child runtime failed", Map.of(), effectiveBudget, effectivePermissions, request);
        }
        Map<String, Object> output = lastOutput(child, run);
        CostAccounting cost = run.cost() == null ? CostAccounting.empty(Map.of("agentRef", request.agentRef())) : run.cost();
        Map<String, Object> provenance = Map.of("agentRef", request.agentRef(), "runId", run.runId());

        if (run.status() == com.unfurl.foundry.substrate.runstate.AgentRunStatus.COMPLETED) {
            AgentTerminalEnvelope terminal = terminalNormalizer.normalize(output);
            if (terminal.status() == AgentTerminalStatus.FAILED) {
                return new AgentDelegationResult(null, terminal.error(), effectiveBudget,
                        effectivePermissions, cost, provenance, request.metadata());
            }
            return new AgentDelegationResult(terminal, null, effectiveBudget,
                    effectivePermissions, cost, provenance, request.metadata());
        }
        FailureCategory category = run.status() == com.unfurl.foundry.substrate.runstate.AgentRunStatus.CANCELLED
                ? FailureCategory.BUSINESS_RULE : FailureCategory.INTERNAL;
        StructuredFailure failure = new StructuredFailure(
                textOr(run.errorCode(), "CHILD_AGENT_FAILED"), category, false, null,
                textOr(run.errorMessage(), "Child agent did not complete"), output,
                Map.of("status", run.status().name()), provenance);
        return new AgentDelegationResult(null, failure, effectiveBudget,
                effectivePermissions, cost, provenance, request.metadata());
    }

    /** Projector: constructs the complete and exclusive child input contract. */
    private Map<String, Object> projectedInput(AgentDelegationRequest request) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("objective", request.objective());
        input.put("context", request.contextProjection());
        input.put("sources", request.sourceRefs());
        if (request.expectedOutputSchemaRef() != null && !request.expectedOutputSchemaRef().isBlank()) {
            input.put("expectedOutputSchemaRef", request.expectedOutputSchemaRef());
        }
        return Map.copyOf(input);
    }

    /** Selector: obtains the last declared phase output, including partial failed output. */
    private Map<String, Object> lastOutput(AgentDefinition child, AgentRunState run) {
        for (int index = child.phases().size() - 1; index >= 0; index--) {
            AgentPhaseState phase = run.phases().get(child.phases().get(index).id());
            if (phase != null && !phase.output().isEmpty()) return phase.output();
        }
        return Map.of();
    }

    /** Factory: creates a pre-execution structured delegation failure. */
    private AgentDelegationResult failure(String code, FailureCategory category, boolean retryable,
                                          String message, Map<String, Object> partialOutput,
                                          BudgetPolicy budget, List<String> permissions,
                                          AgentDelegationRequest request) {
        StructuredFailure failure = new StructuredFailure(code, category, retryable, null, message,
                partialOutput, Map.of(), Map.of("agentRef", request.agentRef()));
        return new AgentDelegationResult(null, failure, budget, permissions,
                CostAccounting.empty(Map.of("agentRef", request.agentRef())), failure.provenance(), request.metadata());
    }

    /** Policy composition: selects the lower non-null ceiling in every budget dimension. */
    static BudgetPolicy lowerOf(BudgetPolicy outer, BudgetPolicy inner) {
        BudgetPolicy left = outer == null ? BudgetPolicy.none() : outer;
        BudgetPolicy right = inner == null ? BudgetPolicy.none() : inner;
        Map<String, Object> metadata = new LinkedHashMap<>(right.metadata());
        metadata.putAll(left.metadata());
        return new BudgetPolicy(min(left.defaultBudgetUsd(), right.defaultBudgetUsd()),
                min(left.maxBudgetUsd(), right.maxBudgetUsd()), min(left.maxPromptTokens(), right.maxPromptTokens()),
                min(left.maxCompletionTokens(), right.maxCompletionTokens()),
                min(left.maxTotalTokens(), right.maxTotalTokens()), metadata);
    }

    /** Numeric helper: treats null as an absent rather than zero ceiling. */
    private static Long min(Long left, Long right) {
        if (left == null) return right;
        if (right == null) return left;
        return Math.min(left, right);
    }

    /** Decimal helper: treats null as an absent rather than zero ceiling. */
    private static BigDecimal min(BigDecimal left, BigDecimal right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.min(right);
    }

    /** Text helper: chooses a stable public fallback for absent runtime diagnostics. */
    private static String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
