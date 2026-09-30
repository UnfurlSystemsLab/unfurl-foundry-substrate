package com.unfurl.foundry.substrate.engine;

import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.agent.AgentPhase;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
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
                    "Pinned child agent was not found", Map.of(), BudgetPolicy.none(), List.of(), List.of(), request);
        }

        BudgetPolicy effectiveBudget = BudgetPolicy.lowerOf(request.budgetEnvelope(), child.budgetPolicy());
        List<String> effectivePermissions = caller.permissions().stream()
                .filter(request.permissionScope()::contains).distinct().toList();
        List<String> declaredTools = declaredTools(child);
        if (request.hasExplicitToolScope() && !declaredTools.containsAll(request.toolScope())) {
            return failure("CHILD_TOOL_SCOPE_INVALID", FailureCategory.AUTHORIZATION, false,
                    "Requested child tool scope contains undeclared tools", Map.of(), effectiveBudget,
                    effectivePermissions, List.of(), request);
        }
        List<String> effectiveTools = request.hasExplicitToolScope()
                ? declaredTools.stream().filter(request.toolScope()::contains).toList()
                : declaredTools;
        AgentDefinition narrowedChild = narrowTools(child, effectiveTools);
        Map<String, Object> childInput = projectedInput(request);
        Map<String, Object> childMetadata = new LinkedHashMap<>();
        childMetadata.put("agentBudgetPolicy", effectiveBudget);
        if (caller.requestId() != null) childMetadata.put("delegatedByRequestId", caller.requestId());
        ExecutionContext childContext = new ExecutionContext(
                caller.tenantId(), caller.userId(), caller.roles(), effectivePermissions,
                caller.correlationId(), caller.requestId(), caller.traceContext(), childMetadata);

        AgentRunState run;
        try {
            run = runtime.start(narrowedChild, childInput, childContext);
        } catch (RuntimeException exception) {
            return failure("CHILD_RUNTIME_FAILED", FailureCategory.TRANSIENT, true,
                    "Child runtime failed", Map.of(), effectiveBudget, effectivePermissions, effectiveTools, request);
        }
        Map<String, Object> output = lastOutput(narrowedChild, run);
        CostAccounting cost = run.cost() == null ? CostAccounting.empty(Map.of("agentRef", request.agentRef())) : run.cost();
        Map<String, Object> provenance = Map.of("agentRef", request.agentRef(), "runId", run.runId());

        if (run.status() == com.unfurl.foundry.substrate.runstate.AgentRunStatus.COMPLETED) {
            AgentTerminalEnvelope terminal = terminalNormalizer.normalize(output);
            if (terminal.status() == AgentTerminalStatus.FAILED) {
                return new AgentDelegationResult(null, terminal.error(), effectiveBudget,
                        effectivePermissions, effectiveTools, cost, provenance, request.metadata());
            }
            return new AgentDelegationResult(terminal, null, effectiveBudget,
                    effectivePermissions, effectiveTools, cost, provenance, request.metadata());
        }
        FailureCategory category = run.status() == com.unfurl.foundry.substrate.runstate.AgentRunStatus.CANCELLED
                ? FailureCategory.BUSINESS_RULE : FailureCategory.INTERNAL;
        StructuredFailure failure = new StructuredFailure(
                textOr(run.errorCode(), "CHILD_AGENT_FAILED"), category, false, null,
                textOr(run.errorMessage(), "Child agent did not complete"), output,
                Map.of("status", run.status().name()), provenance);
        return new AgentDelegationResult(null, failure, effectiveBudget,
                effectivePermissions, effectiveTools, cost, provenance, request.metadata());
    }

    /** Selector: derives the complete stable child-declared tool set from agent and phase declarations. */
    private List<String> declaredTools(AgentDefinition child) {
        LinkedHashSet<String> declared = new LinkedHashSet<>(child.toolRefs());
        child.phases().forEach(phase -> declared.addAll(phase.allowedToolRefs()));
        return List.copyOf(declared);
    }

    /** Projector: creates a child definition whose agent- and phase-level tools cannot exceed effective scope. */
    private AgentDefinition narrowTools(AgentDefinition child, List<String> effectiveTools) {
        List<AgentPhase> phases = child.phases().stream().map(phase -> new AgentPhase(
                phase.id(), phase.promptTemplateRef(), phase.modelRef(),
                phase.allowedToolRefs().stream().filter(effectiveTools::contains).toList(),
                phase.ragQueryRef(), phase.input(), phase.outputMapping(), phase.dependencies(),
                phase.maxToolIterations(), phase.skillRefs(), phase.outputSchemaRef(),
                phase.semanticValidatorRef(), phase.correctionPolicy())).toList();
        return new AgentDefinition(child.id(), child.version(), child.metadata(), phases, child.edges(),
                child.inputSchema(), child.defaultModelRef(), effectiveTools, child.budgetPolicy(), child.skillRefs());
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
                                          List<String> tools,
                                          AgentDelegationRequest request) {
        StructuredFailure failure = new StructuredFailure(code, category, retryable, null, message,
                partialOutput, Map.of(), Map.of("agentRef", request.agentRef()));
        return new AgentDelegationResult(null, failure, budget, permissions, tools,
                CostAccounting.empty(Map.of("agentRef", request.agentRef())), failure.provenance(), request.metadata());
    }

    /** Text helper: chooses a stable public fallback for absent runtime diagnostics. */
    private static String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
