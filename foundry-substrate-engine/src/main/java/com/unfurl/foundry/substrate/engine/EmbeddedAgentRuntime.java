package com.unfurl.foundry.substrate.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.agent.AgentDefinitionValidator;
import com.unfurl.foundry.substrate.agent.AgentPhase;
import com.unfurl.foundry.substrate.agent.CorrectionExhaustionAction;
import com.unfurl.foundry.substrate.events.AgentEvent;
import com.unfurl.foundry.substrate.events.AgentEventType;
import com.unfurl.foundry.substrate.guardrail.CostGuardrail;
import com.unfurl.foundry.substrate.guardrail.BudgetPolicyCostGuardrail;
import com.unfurl.foundry.substrate.guardrail.CostGuardrailContext;
import com.unfurl.foundry.substrate.guardrail.GuardrailDecision;
import com.unfurl.foundry.substrate.guardrail.PermissionBridge;
import com.unfurl.foundry.substrate.guardrail.PermissionDecision;
import com.unfurl.foundry.substrate.model.Message;
import com.unfurl.foundry.substrate.model.ModelRequest;
import com.unfurl.foundry.substrate.model.ModelResponse;
import com.unfurl.foundry.substrate.model.ModelToolCall;
import com.unfurl.foundry.substrate.model.ModelTurnOutcome;
import com.unfurl.foundry.substrate.ports.AgentEventSink;
import com.unfurl.foundry.substrate.ports.AgentRuntime;
import com.unfurl.foundry.substrate.ports.ModelProvider;
import com.unfurl.foundry.substrate.ports.OutputSchemaValidator;
import com.unfurl.foundry.substrate.ports.ProviderRegistry;
import com.unfurl.foundry.substrate.ports.RagRetriever;
import com.unfurl.foundry.substrate.ports.SemanticValidator;
import com.unfurl.foundry.substrate.ports.SemanticValidatorRegistry;
import com.unfurl.foundry.substrate.ports.ToolCallRequest;
import com.unfurl.foundry.substrate.ports.ToolCallDecision;
import com.unfurl.foundry.substrate.ports.ToolCallDecisionType;
import com.unfurl.foundry.substrate.ports.ToolCallInterceptorChain;
import com.unfurl.foundry.substrate.ports.ToolCallResult;
import com.unfurl.foundry.substrate.ports.ToolExecutor;
import com.unfurl.foundry.substrate.ports.ToolRegistry;
import com.unfurl.foundry.substrate.ports.ValidationIssue;
import com.unfurl.foundry.substrate.ports.ValidationResult;
import com.unfurl.foundry.substrate.prompt.PromptAssembler;
import com.unfurl.foundry.substrate.prompt.PromptTemplate;
import com.unfurl.foundry.substrate.rag.Chunk;
import com.unfurl.foundry.substrate.rag.RagQuery;
import com.unfurl.foundry.substrate.rag.RagResult;
import com.unfurl.foundry.substrate.resolver.DataReferenceResolver;
import com.unfurl.foundry.substrate.runstate.AgentPhaseState;
import com.unfurl.foundry.substrate.runstate.AgentPhaseStatus;
import com.unfurl.foundry.substrate.runstate.AgentRunState;
import com.unfurl.foundry.substrate.runstate.AgentRunStatus;
import com.unfurl.foundry.substrate.runstate.CostAccounting;
import com.unfurl.foundry.substrate.runstate.ToolCall;
import com.unfurl.substrate.domain.ConditionDefinition;
import com.unfurl.substrate.domain.EdgeDefinition;
import com.unfurl.substrate.policy.ExecutionContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequential, in-process multi-phase agent runner — the AI peer of the substrate's
 * EmbeddedExecutionEngine. Uses injected ports and an in-memory run store. Durable,
 * distributed, and streaming execution belong to {@code unfurl-foundry}.
 *
 * <p>The {@link DataReferenceResolver} is wired into phase-input resolution from day one
 * (the lesson from the substrate review). Phase scheduling is dependency- and edge-driven;
 * conditional inbound edges route phases to RUN or SKIPPED.
 */
public final class EmbeddedAgentRuntime implements AgentRuntime {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ProviderRegistry providerRegistry;
    private final ToolRegistry toolRegistry;
    private final RagRetriever ragRetriever;
    private final CostGuardrail costGuardrail;
    private final PermissionBridge permissionBridge;
    private final ToolCallInterceptorChain toolCallInterceptors;
    private final OutputSchemaValidator outputSchemaValidator;
    private final SemanticValidatorRegistry semanticValidatorRegistry;
    private final AgentEventSink eventSink;
    private final PromptAssembler promptAssembler;
    private final DataReferenceResolver resolver;
    private final AgentDefinitionValidator validator;
    private final AgentRunStore store;
    private final Map<String, PromptTemplate> templates;

/**
 * Constructs EmbeddedAgentRuntime with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public EmbeddedAgentRuntime(ProviderRegistry providerRegistry, ToolRegistry toolRegistry) {
        this(providerRegistry, toolRegistry, null, new BudgetPolicyCostGuardrail(), new AllowAllPermissionBridge(),
                defaultEventSink(), new PromptAssembler(), new DataReferenceResolver(),
                new AgentDefinitionValidator(), defaultStore(), Map.of(), ToolCallInterceptorChain.empty(),
                null, null);
    }

/**
 * Constructs EmbeddedAgentRuntime with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public EmbeddedAgentRuntime(
            ProviderRegistry providerRegistry,
            ToolRegistry toolRegistry,
            RagRetriever ragRetriever,
            CostGuardrail costGuardrail,
            PermissionBridge permissionBridge,
            AgentEventSink eventSink,
            PromptAssembler promptAssembler,
            DataReferenceResolver resolver,
            AgentDefinitionValidator validator,
            AgentRunStore store,
            Map<String, PromptTemplate> templates
    ) {
        this(providerRegistry, toolRegistry, ragRetriever, costGuardrail, permissionBridge, eventSink,
                promptAssembler, resolver, validator, store, templates, ToolCallInterceptorChain.empty(),
                null, null);
    }

    /**
     * Constructs the embedded runtime with a host-supplied deterministic tool policy chain while
     * preserving the legacy constructor as a no-op-chain compatibility path.
     */
    public EmbeddedAgentRuntime(
            ProviderRegistry providerRegistry,
            ToolRegistry toolRegistry,
            RagRetriever ragRetriever,
            CostGuardrail costGuardrail,
            PermissionBridge permissionBridge,
            AgentEventSink eventSink,
            PromptAssembler promptAssembler,
            DataReferenceResolver resolver,
            AgentDefinitionValidator validator,
            AgentRunStore store,
            Map<String, PromptTemplate> templates,
            ToolCallInterceptorChain toolCallInterceptors
    ) {
        this(providerRegistry, toolRegistry, ragRetriever, costGuardrail, permissionBridge, eventSink,
                promptAssembler, resolver, validator, store, templates, toolCallInterceptors, null, null);
    }

    /**
     * Constructs the runtime with host-owned structural and semantic validation ports. Declared
     * validation references fail closed when either port cannot resolve its binding.
     */
    public EmbeddedAgentRuntime(
            ProviderRegistry providerRegistry,
            ToolRegistry toolRegistry,
            RagRetriever ragRetriever,
            CostGuardrail costGuardrail,
            PermissionBridge permissionBridge,
            AgentEventSink eventSink,
            PromptAssembler promptAssembler,
            DataReferenceResolver resolver,
            AgentDefinitionValidator validator,
            AgentRunStore store,
            Map<String, PromptTemplate> templates,
            ToolCallInterceptorChain toolCallInterceptors,
            OutputSchemaValidator outputSchemaValidator,
            SemanticValidatorRegistry semanticValidatorRegistry
    ) {
        this.providerRegistry = providerRegistry;
        this.toolRegistry = toolRegistry;
        this.ragRetriever = ragRetriever;
        this.costGuardrail = costGuardrail;
        this.permissionBridge = permissionBridge;
        this.toolCallInterceptors = toolCallInterceptors == null
                ? ToolCallInterceptorChain.empty() : toolCallInterceptors;
        this.outputSchemaValidator = outputSchemaValidator;
        this.semanticValidatorRegistry = semanticValidatorRegistry;
        this.eventSink = eventSink == null ? defaultEventSink() : eventSink;
        this.promptAssembler = promptAssembler;
        this.resolver = resolver;
        this.validator = validator;
        this.store = store == null ? defaultStore() : store;
        this.templates = Map.copyOf(templates);
    }

/**
 * Null Object factory: supplies the default no-I/O event sink used by the embedded runtime when a
 * host does not bind an event port.
 */
    private static AgentEventSink defaultEventSink() {
        return (event, context) -> {
        };
    }

/**
 * Factory method: creates the runtime's default in-memory run store without requiring callers to
 * reference a separate concrete store class.
 */
    private static AgentRunStore defaultStore() {
        ConcurrentHashMap<String, AgentRunState> runs = new ConcurrentHashMap<>();
        return new AgentRunStore() {
            /**
             * Store operation: records the latest run snapshot by run id for single-process embedded execution.
             */
            @Override
            public void save(AgentRunState run, ExecutionContext context) {
                runs.put(run.runId(), run);
            }

            /**
             * Store operation: loads the latest in-memory run snapshot for resume/cancel lookups.
             */
            @Override
            public java.util.Optional<AgentRunState> load(String runId, ExecutionContext context) {
                return java.util.Optional.ofNullable(runs.get(runId));
            }
        };
    }

/**
 * Performs the start operation for this component, translating validated inputs into the domain result expected by callers.
 */
    @Override
    public AgentRunState start(AgentDefinition agent, Map<String, Object> input, ExecutionContext context) {
        validator.validate(agent);
        String runId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Map<String, AgentPhaseState> phaseStates = new LinkedHashMap<>();
        for (AgentPhase phase : agent.phases()) {
            phaseStates.put(phase.id(), AgentPhaseState.pending(phase.id()));
        }
        Map<String, Object> attribution = new LinkedHashMap<>();
        if (context != null && context.tenantId() != null) {
            attribution.put("tenantId", context.tenantId());
        }
        attribution.put("runId", runId);
        attribution.put("agentId", agent.id());

        State state = new State(
                context == null ? null : context.tenantId(),
                runId, agent, input == null ? Map.of() : Map.copyOf(input),
                phaseStates, CostAccounting.empty(attribution), now);
        save(state, AgentRunStatus.RUNNING, context);
        emit(context, state, null, AgentEventType.AGENT_STARTED, Map.of("agentId", agent.id()));

        return runScheduler(state, context);
    }

/**
 * Performs the resume operation for this component, translating validated inputs into the domain result expected by callers.
 */
    @Override
    public AgentRunState resume(String runId, Map<String, Object> signal, ExecutionContext context) {
        AgentRunState loaded = store.load(runId, context)
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
        // The in-memory runner has no suspend point in this slice, so resume reloads and
        // returns. Durable suspend/resume is unfurl-foundry's responsibility.
        return loaded;
    }

/**
 * Performs the cancel operation for this component, translating validated inputs into the domain result expected by callers.
 */
    @Override
    public AgentRunState cancel(String runId, ExecutionContext context) {
        AgentRunState current = store.load(runId, context)
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
        Map<String, AgentPhaseState> phases = new LinkedHashMap<>(current.phases());
        phases.replaceAll((id, ps) -> isTerminal(ps.status()) ? ps
                : new AgentPhaseState(id, AgentPhaseStatus.CANCELLED, ps.input(), ps.messages(), ps.toolCalls(),
                ps.output(), ps.errorCode(), ps.errorMessage(), ps.startedAt(), Instant.now()));
        AgentRunState cancelled = withStatus(current, phases, AgentRunStatus.CANCELLED, null, null);
        store.save(cancelled, context);
        emitRun(context, cancelled, AgentEventType.AGENT_CANCELLED, Map.of());
        return cancelled;
    }

/**
 * Implements the runScheduler helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private AgentRunState runScheduler(State state, ExecutionContext context) {
        boolean progressed;
        do {
            progressed = false;
            for (AgentPhase phase : state.agent.phases()) {
                AgentPhaseState ps = state.phases.get(phase.id());
                if (ps.status() != AgentPhaseStatus.PENDING || !predecessorsTerminal(state, phase)) {
                    continue;
                }
                progressed = true;
                if (isEdgeGated(state.agent, phase) && !anyInboundEdgeSatisfied(state, phase)) {
                    markPhase(state, phase.id(), skipped(phase.id()));
                    emit(context, state, phase.id(), AgentEventType.PHASE_SKIPPED, Map.of());
                    continue;
                }
                AgentPhaseState result = executePhase(state, phase, context);
                markPhase(state, phase.id(), result);
                if (result.status() == AgentPhaseStatus.FAILED) {
                    emit(context, state, phase.id(), AgentEventType.PHASE_FAILED,
                            Map.of("errorCode", result.errorCode(), "errorMessage", result.errorMessage()));
                    AgentRunState failed = save(state, AgentRunStatus.FAILED, context, result.errorCode(), result.errorMessage());
                    emitRun(context, failed, AgentEventType.AGENT_FAILED, Map.of("failedPhase", phase.id()));
                    return failed;
                }
                emit(context, state, phase.id(), AgentEventType.PHASE_COMPLETED, Map.of());
                save(state, AgentRunStatus.RUNNING, context);
            }
        } while (progressed);

        List<String> pending = state.phases.values().stream()
                .filter(ps -> !isTerminal(ps.status()))
                .map(AgentPhaseState::phaseId)
                .toList();
        if (!pending.isEmpty()) {
            String message = "No schedulable phases remain: " + pending;
            AgentRunState failed = save(state, AgentRunStatus.FAILED, context, "SCHEDULE_STALLED", message);
            emitRun(context, failed, AgentEventType.AGENT_FAILED, Map.of("pendingPhases", pending));
            return failed;
        }

        AgentRunState completed = save(state, AgentRunStatus.COMPLETED, context);
        emitRun(context, completed, AgentEventType.AGENT_COMPLETED, Map.of());
        return completed;
    }

/**
 * Performs the executePhase operation for this component, translating validated inputs into the domain result expected by callers.
 */
    private AgentPhaseState executePhase(State state, AgentPhase phase, ExecutionContext context) {
        Instant started = Instant.now();
        Map<String, Object> resolvedInput = resolver.resolveInput(phase.input(), state.agentInput, phaseOutputs(state));
        emit(context, state, phase.id(), AgentEventType.PHASE_STARTED, Map.of());

        List<Message> messages = assemble(phase, resolvedInput);

        if (phase.ragQueryRef() != null && ragRetriever != null) {
            String query = String.valueOf(resolvedInput.getOrDefault("query", resolvedInput.getOrDefault("prompt", "")));
            RagResult rag = ragRetriever.retrieve(new RagQuery(query, 5, Map.of(), phase.ragQueryRef(), Map.of()), context);
            emit(context, state, phase.id(), AgentEventType.RAG_RETRIEVED, Map.of("chunks", rag.chunks().size()));
            messages.add(Message.system(joinChunks(rag)));
        }

        ExecutionContext guardrailContext = CostGuardrailContext.withAgentBudgetPolicy(context, state.agent.budgetPolicy());
        GuardrailDecision guardrail = costGuardrail.check(state.cost, guardrailContext);
        if (!guardrail.allowed()) {
            emit(context, state, phase.id(), AgentEventType.GUARDRAIL_TRIPPED, Map.of("reason", String.valueOf(guardrail.reason())));
            return failedPhase(phase.id(), resolvedInput, messages, "GUARDRAIL_TRIPPED", guardrail.reason(), started);
        }

        String modelRef = phase.modelRef() != null ? phase.modelRef() : state.agent.defaultModelRef();
        ModelProvider provider = providerRegistry.resolveModel(modelRef, context).orElse(null);
        if (provider == null) {
            return failedPhase(phase.id(), resolvedInput, messages, "PROVIDER_MISSING", "No model provider for ref: " + modelRef, started);
        }

        List<ToolCall> toolCalls = new ArrayList<>();
        ModelCallOutcome firstCall = callModelOutcome(state, phase, provider, messages, modelRef, context, resolvedInput, started);
        if (!firstCall.success()) {
            return firstCall.failure();
        }
        ModelResponse response = firstCall.response();
        messages.add(response.message() == null ? Message.assistant("") : response.message());

        int iterations = 0;
        while (response.hasToolCalls() && iterations < phase.maxToolIterations()) {
            for (ModelToolCall call : response.toolCalls()) {
                if (!isToolAllowed(state.agent, phase, call.toolName())) {
                    return failedPhase(phase.id(), resolvedInput, messages, "TOOL_NOT_ALLOWED",
                            "Tool not allowed for phase: " + call.toolName(), started);
                }
                ToolCallRequest originalRequest = new ToolCallRequest(
                        call.id(), call.toolName(), call.arguments(), Map.of());
                ToolCallDecision interceptorDecision = toolCallInterceptors.before(originalRequest, context);
                Map<String, Object> effectiveArguments = interceptorDecision.arguments();
                if (interceptorDecision.type() != ToolCallDecisionType.ALLOW) {
                    ToolCallResult denied = ToolCallResult.failure(interceptorDecision.failure())
                            .withMetadata(interceptorDecision.metadata());
                    toolCalls.add(new ToolCall(call.id(), call.toolName(), effectiveArguments,
                            denied.output(), denied.errorCode(), denied.errorMessage(), Instant.now(), Instant.now()));
                    emit(context, state, phase.id(), AgentEventType.TOOL_FAILED, Map.of(
                            "tool", call.toolName(),
                            "errorCode", denied.errorCode(),
                            "decision", interceptorDecision.type().name()));
                    return failedToolPhase(phase.id(), resolvedInput, messages, toolCalls, denied, started);
                }
                PermissionDecision permission = permissionBridge.check(call.toolName(), effectiveArguments, context);
                if (!permission.allowed()) {
                    return failedPhase(phase.id(), resolvedInput, messages, "PERMISSION_DENIED",
                            "Tool not permitted: " + call.toolName(), started);
                }
                ToolExecutor executor = toolRegistry.resolveTool(call.toolName(), context).orElse(null);
                if (executor == null) {
                    return failedPhase(phase.id(), resolvedInput, messages, "TOOL_MISSING",
                            "No executor for tool: " + call.toolName(), started);
                }
                emit(context, state, phase.id(), AgentEventType.TOOL_CALLED, Map.of("tool", call.toolName()));
                Instant toolStart = Instant.now();
                ToolCallRequest effectiveRequest = new ToolCallRequest(
                        call.id(), call.toolName(), effectiveArguments, interceptorDecision.metadata());
                ToolCallResult toolResult = executor.execute(effectiveRequest, context);
                toolResult = toolCallInterceptors.after(effectiveRequest, toolResult, context);
                toolCalls.add(new ToolCall(call.id(), call.toolName(), effectiveArguments,
                        toolResult.output(), toolResult.errorCode(), toolResult.errorMessage(), toolStart, Instant.now()));
                if (!toolResult.success()) {
                    emit(context, state, phase.id(), AgentEventType.TOOL_FAILED, Map.of(
                            "tool", call.toolName(),
                            "errorCode", toolResult.errorCode(),
                            "category", toolResult.failure().category().name(),
                            "retryable", toolResult.failure().retryable()));
                    return failedToolPhase(phase.id(), resolvedInput, messages, toolCalls, toolResult,
                            started);
                }
                emit(context, state, phase.id(), AgentEventType.TOOL_COMPLETED, Map.of("tool", call.toolName()));
                String toolMessageContent;
                try {
                    toolMessageContent = toolResultContent(toolResult.output());
                } catch (IllegalArgumentException ex) {
                    return failedPhase(phase.id(), resolvedInput, messages, "TOOL_RESULT_SERIALIZATION_FAILED",
                            ex.getMessage(), started);
                }
                messages.add(new Message(
                        com.unfurl.foundry.substrate.model.MessageRole.TOOL,
                        toolMessageContent,
                        call.id(),
                        Map.of("toolName", call.toolName())));
            }
            java.util.Optional<AgentPhaseState> mappedToolPhase = mappedToolPhaseIfComplete(
                    phase,
                    resolvedInput,
                    messages,
                    response.message() == null ? "" : response.message().content(),
                    toolCalls,
                    context,
                    started);
            if (mappedToolPhase.isPresent()) {
                return mappedToolPhase.get();
            }
            iterations++;
            ModelCallOutcome nextCall = callModelOutcome(state, phase, provider, messages, modelRef, context, resolvedInput, started);
            if (!nextCall.success()) {
                return nextCall.failure();
            }
            response = nextCall.response();
            messages.add(response.message() == null ? Message.assistant("") : response.message());
        }
        if (response.hasToolCalls()) {
            return failedPhase(phase.id(), resolvedInput, messages, "MAX_TOOL_ITERATIONS_EXCEEDED",
                    "Model requested tool calls after " + phase.maxToolIterations() + " iterations", started);
        }

        String content = response.message() == null ? "" : response.message().content();
        return validateAndCorrectPhase(state, phase, provider, modelRef, resolvedInput, messages,
                toolCalls, content == null ? "" : content, context, started);
    }

    /**
     * Validation Pipeline: enforces structural, mapping, and semantic ordering and performs only
     * the finite correction turns allowed by the phase policy.
     */
    private AgentPhaseState validateAndCorrectPhase(
            State state,
            AgentPhase phase,
            ModelProvider provider,
            String modelRef,
            Map<String, Object> resolvedInput,
            List<Message> messages,
            List<ToolCall> toolCalls,
            String initialContent,
            ExecutionContext context,
            Instant started) {
        String content = initialContent;
        Instant correctionStarted = Instant.now();
        int attempts = 0;
        while (true) {
            OutputValidation validation = validateOutput(phase, content, toolCalls, context);
            if (validation.valid()) {
                return completedPhase(phase, resolvedInput, messages, toolCalls, validation.output(), started);
            }
            emit(context, state, phase.id(), AgentEventType.OUTPUT_VALIDATION_FAILED, Map.of(
                    "issueCodes", validation.issues().stream().map(ValidationIssue::code).toList(),
                    "attempt", attempts));
            boolean withinAttempts = attempts < phase.correctionPolicy().maxAttempts();
            boolean withinDuration = phase.correctionPolicy().maxDurationMillis() == 0
                    || java.time.Duration.between(correctionStarted, Instant.now()).toMillis()
                    < phase.correctionPolicy().maxDurationMillis();
            if (!withinAttempts || !withinDuration) {
                return exhaustedValidation(phase, resolvedInput, messages, toolCalls, validation, started);
            }
            attempts++;
            emit(context, state, phase.id(), AgentEventType.OUTPUT_CORRECTION_REQUESTED,
                    Map.of("attempt", attempts));
            messages.add(Message.user(correctionFeedback(validation.issues(), attempts)));
            ModelCallOutcome corrected = callModelOutcome(
                    state, phase, provider, messages, modelRef, context, resolvedInput, started);
            if (!corrected.success()) {
                return corrected.failure();
            }
            ModelResponse response = corrected.response();
            if (java.time.Duration.between(correctionStarted, Instant.now()).toMillis()
                    >= phase.correctionPolicy().maxDurationMillis()) {
                return exhaustedValidation(
                        phase, resolvedInput, messages, toolCalls, validation, started);
            }
            if (response.hasToolCalls()) {
                return failedPhase(phase.id(), resolvedInput, messages,
                        "VALIDATION_CORRECTION_TOOL_REQUESTED",
                        "Correction turn requested tools instead of corrected structured output", started);
            }
            messages.add(response.message() == null ? Message.assistant("") : response.message());
            content = response.message() == null ? "" : response.message().content();
        }
    }

    /** Validation Pipeline step: structural schema, output mapping, then semantic validation. */
    private OutputValidation validateOutput(
            AgentPhase phase, String content, List<ToolCall> toolCalls, ExecutionContext context) {
        Map<String, Object> raw = rawPhaseOutput(content);
        if (phase.outputSchemaRef() != null && !phase.outputSchemaRef().isBlank()) {
            if (outputSchemaValidator == null) {
                return OutputValidation.invalid(List.of(new ValidationIssue(
                        "OUTPUT_SCHEMA_BINDING_MISSING", "$", "No output schema validator is bound", Map.of())));
            }
            Object structured = raw.getOrDefault("json", raw);
            ValidationResult structural;
            try {
                structural = outputSchemaValidator.validate(phase.outputSchemaRef(), structured, context);
            } catch (RuntimeException exception) {
                return OutputValidation.invalid(List.of(new ValidationIssue(
                        "OUTPUT_SCHEMA_VALIDATOR_FAILED", "$",
                        "Output schema validator failed", Map.of())));
            }
            if (!structural.valid()) {
                return OutputValidation.invalid(structural.issues());
            }
        }
        PhaseOutput mapped = applyOutputMapping(phase, raw, toolCalls);
        if (!mapped.success()) {
            return OutputValidation.invalid(List.of(new ValidationIssue(
                    mapped.errorCode(), "$", mapped.errorMessage(), Map.of())));
        }
        if (phase.semanticValidatorRef() != null && !phase.semanticValidatorRef().isBlank()) {
            if (semanticValidatorRegistry == null) {
                return OutputValidation.invalid(List.of(new ValidationIssue(
                        "SEMANTIC_VALIDATOR_BINDING_MISSING", "$",
                        "No semantic validator registry is bound", Map.of())));
            }
            SemanticValidator semantic = semanticValidatorRegistry
                    .resolve(phase.semanticValidatorRef(), context).orElse(null);
            if (semantic == null) {
                return OutputValidation.invalid(List.of(new ValidationIssue(
                        "SEMANTIC_VALIDATOR_MISSING", "$",
                        "Semantic validator is not registered: " + phase.semanticValidatorRef(), Map.of())));
            }
            ValidationResult result;
            try {
                result = semantic.validate(mapped.values(), raw, context);
            } catch (RuntimeException exception) {
                return OutputValidation.invalid(List.of(new ValidationIssue(
                        "SEMANTIC_VALIDATOR_FAILED", "$", "Semantic validator failed", Map.of())));
            }
            if (!result.valid()) {
                return OutputValidation.invalid(result.issues());
            }
        }
        return OutputValidation.valid(mapped.values());
    }

    /** Exhaustion Strategy: returns either a structured failure or an escalation terminal output. */
    private AgentPhaseState exhaustedValidation(
            AgentPhase phase,
            Map<String, Object> resolvedInput,
            List<Message> messages,
            List<ToolCall> toolCalls,
            OutputValidation validation,
            Instant started) {
        if (phase.correctionPolicy().exhaustionAction() == CorrectionExhaustionAction.ESCALATE) {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("kind", "escalated");
            output.put("reason", "validation correction exhausted");
            output.put("validationIssues", issueMaps(validation.issues()));
            return completedPhase(phase, resolvedInput, messages, toolCalls, output, started);
        }
        String errorCode = validation.issues().size() == 1
                && "OUTPUT_MAPPING_UNRESOLVED".equals(validation.issues().getFirst().code())
                ? "OUTPUT_MAPPING_UNRESOLVED" : "VALIDATION_FAILED";
        return failedPhase(phase.id(), resolvedInput, messages, errorCode,
                validation.issues().stream().map(ValidationIssue::message)
                        .collect(java.util.stream.Collectors.joining("; ")), started);
    }

    /** Factory: creates the immutable successful phase snapshot shared by validation paths. */
    private AgentPhaseState completedPhase(
            AgentPhase phase,
            Map<String, Object> resolvedInput,
            List<Message> messages,
            List<ToolCall> toolCalls,
            Map<String, Object> output,
            Instant started) {
        return new AgentPhaseState(phase.id(), AgentPhaseStatus.COMPLETED, resolvedInput,
                List.copyOf(messages), List.copyOf(toolCalls), output, null, null, started, Instant.now());
    }

    /** Feedback Adapter: serializes only precise, sanitized issues into the correction turn. */
    private String correctionFeedback(List<ValidationIssue> issues, int attempt) {
        try {
            return "Correct the structured output. Return only the corrected result. Validation attempt "
                    + attempt + " issues: " + MAPPER.writeValueAsString(issueMaps(issues));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("could not serialize validation feedback", exception);
        }
    }

    /** Projection helper: converts immutable validation issues to provider-neutral JSON maps. */
    private List<Map<String, Object>> issueMaps(List<ValidationIssue> issues) {
        return issues.stream().map(issue -> Map.<String, Object>of(
                "code", issue.code(), "path", issue.path(), "message", issue.message()))
                .toList();
    }

/**
 * Strategy: completes deterministic tool phases directly from outputMapping when
 * the mapping is fully satisfied by the executed tool calls.
 */
    private java.util.Optional<AgentPhaseState> mappedToolPhaseIfComplete(
            AgentPhase phase,
            Map<String, Object> resolvedInput,
            List<Message> messages,
            String content,
            List<ToolCall> toolCalls,
            ExecutionContext context,
            Instant started
    ) {
        if (phase.outputMapping().isEmpty() || toolCalls.isEmpty()) {
            return java.util.Optional.empty();
        }
        PhaseOutput output = buildPhaseOutput(phase, content == null ? "" : content, toolCalls);
        if (!output.success()) {
            return java.util.Optional.empty();
        }
        if ((phase.outputSchemaRef() != null && !phase.outputSchemaRef().isBlank())
                || (phase.semanticValidatorRef() != null && !phase.semanticValidatorRef().isBlank())) {
            OutputValidation validation = validateOutput(phase, content, toolCalls, context);
            if (!validation.valid()) {
                return java.util.Optional.empty();
            }
            output = PhaseOutput.success(validation.output());
        }
        return java.util.Optional.of(new AgentPhaseState(
                phase.id(),
                AgentPhaseStatus.COMPLETED,
                resolvedInput,
                List.copyOf(messages),
                List.copyOf(toolCalls),
                output.values(),
                null,
                null,
                started,
                Instant.now()));
    }

/**
 * Builder: turns raw model content and tool results into the structured phase output consumed by downstream phases.
 */
    @SuppressWarnings("unchecked")
    private PhaseOutput buildPhaseOutput(AgentPhase phase, String content, List<ToolCall> toolCalls) {
        return applyOutputMapping(phase, rawPhaseOutput(content), toolCalls);
    }

    /** Builder: exposes raw text and parsed JSON before any output mapping is evaluated. */
    private Map<String, Object> rawPhaseOutput(String content) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("content", content);
        parseJsonObject(content).ifPresent(parsed -> {
            output.put("json", parsed);
            parsed.forEach((key, value) -> {
                if (!"content".equals(key) && !"json".equals(key)) {
                    output.putIfAbsent(key, value);
                }
            });
        });
        return output;
    }

    /** Mapping Pipeline step: resolves declared output mappings against structurally valid output. */
    private PhaseOutput applyOutputMapping(
            AgentPhase phase, Map<String, Object> rawOutput, List<ToolCall> toolCalls) {
        Map<String, Object> output = new LinkedHashMap<>(rawOutput);

        if (phase.outputMapping().isEmpty()) {
            return PhaseOutput.success(output);
        }

        Map<String, Object> mappingScope = Map.of(
                "output", output,
                "tools", toolOutputs(toolCalls));
        for (Map.Entry<String, Object> entry : phase.outputMapping().entrySet()) {
            MappingResolution resolution = resolveMapping(entry.getValue(), mappingScope);
            if (!resolution.resolved()) {
                return PhaseOutput.failure("OUTPUT_MAPPING_UNRESOLVED",
                        "Could not resolve outputMapping." + entry.getKey() + " from " + entry.getValue());
            }
            if (!"content".equals(entry.getKey())) {
                output.put(entry.getKey(), resolution.value());
            }
        }
        return PhaseOutput.success(output);
    }

/**
 * Adapter: exposes tool-call results under stable mapping paths such as {@code $.tools.<callId>.output}.
 */
    private Map<String, Object> toolOutputs(List<ToolCall> toolCalls) {
        Map<String, Object> tools = new LinkedHashMap<>();
        for (ToolCall call : toolCalls) {
            tools.put(call.callId(), Map.of(
                    "toolName", call.toolName() == null ? "" : call.toolName(),
                    "arguments", call.arguments(),
                    "output", call.result(),
                    "errorCode", call.errorCode() == null ? "" : call.errorCode(),
                    "errorMessage", call.errorMessage() == null ? "" : call.errorMessage()));
        }
        return tools;
    }

/**
 * Strategy: resolves nested outputMapping values against the phase-local mapping scope.
 */
    private MappingResolution resolveMapping(Object value, Map<String, Object> scope) {
        if (value instanceof String expression && expression.startsWith("$.")) {
            return resolvePath(expression, scope)
                    .map(MappingResolution::success)
                    .orElseGet(MappingResolution::unresolved);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> resolved = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                MappingResolution next = resolveMapping(entry.getValue(), scope);
                if (!next.resolved()) {
                    return MappingResolution.unresolved();
                }
                resolved.put(String.valueOf(entry.getKey()), next.value());
            }
            return MappingResolution.success(Map.copyOf(resolved));
        }
        if (value instanceof List<?> list) {
            List<Object> resolved = new ArrayList<>();
            for (Object item : list) {
                MappingResolution next = resolveMapping(item, scope);
                if (!next.resolved()) {
                    return MappingResolution.unresolved();
                }
                resolved.add(next.value());
            }
            return MappingResolution.success(List.copyOf(resolved));
        }
        return MappingResolution.success(value);
    }

/**
 * Performs a simple object-path walk for phase-local mapping expressions.
 */
    private java.util.Optional<Object> resolvePath(String expression, Map<String, Object> scope) {
        Object value = scope;
        for (String segment : expression.substring(2).split("\\.")) {
            if (value instanceof Map<?, ?> map && map.containsKey(segment)) {
                value = map.get(segment);
            } else {
                return java.util.Optional.empty();
            }
        }
        return java.util.Optional.ofNullable(value);
    }

/**
 * Parser: extracts a JSON object from model output without making free-form output invalid by default.
 */
    private java.util.Optional<Map<String, Object>> parseJsonObject(String content) {
        if (content == null || content.isBlank()) {
            return java.util.Optional.empty();
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(MAPPER.readValue(content.substring(start, end + 1), MAP_TYPE));
        } catch (JsonProcessingException ex) {
            return java.util.Optional.empty();
        }
    }

/**
 * Boundary adapter: converts provider exceptions into structured agent phase failures so callers
 * receive the substrate error model instead of a thrown SDK/runtime exception.
 */
    private ModelCallOutcome callModelOutcome(
            State state,
            AgentPhase phase,
            ModelProvider provider,
            List<Message> messages,
            String modelRef,
            ExecutionContext context,
            Map<String, Object> resolvedInput,
        Instant started) {
        try {
            ModelResponse response = callModel(state, phase, provider, messages, modelRef, context);
            return switch (response.outcome()) {
                case COMPLETED, TOOL_REQUESTED -> ModelCallOutcome.success(response);
                case MAX_OUTPUT_REACHED -> modelOutcomeFailure(
                        phase, resolvedInput, messages, "MODEL_MAX_OUTPUT_REACHED",
                        "Model reached its output limit before completing the turn", started);
                case CONTENT_FILTERED -> modelOutcomeFailure(
                        phase, resolvedInput, messages, "MODEL_CONTENT_FILTERED",
                        "Model response was filtered by the configured provider", started);
                case PROVIDER_ERROR -> modelOutcomeFailure(
                        phase, resolvedInput, messages, "MODEL_PROVIDER_ERROR",
                        "Model provider returned an unrecognized or error completion outcome", started);
            };
        } catch (RuntimeException ex) {
            return ModelCallOutcome.failure(failedPhase(
                    phase.id(),
                    resolvedInput,
                    messages,
                    "MODEL_FAILED",
                    safeModelFailureMessage(modelRef, ex),
                    started));
        }
    }

    /**
     * Factory: converts a non-successful neutral model outcome into the same structured phase failure
     * used for provider exceptions, keeping provider-native reason strings out of runtime branching.
     */
    private ModelCallOutcome modelOutcomeFailure(
            AgentPhase phase,
            Map<String, Object> resolvedInput,
            List<Message> messages,
            String code,
            String message,
            Instant started) {
        return ModelCallOutcome.failure(failedPhase(
                phase.id(), resolvedInput, messages, code, message, started));
    }

/**
 * Provider call strategy: sends the normalized model request through the neutral provider port and
 * records token/cost metadata only after the provider returns a usable response.
 */
    private ModelResponse callModel(State state, AgentPhase phase, ModelProvider provider, List<Message> messages,
                                    String modelRef, ExecutionContext context) {
        List<Map<String, Object>> toolSchemas = toolSchemas(state.agent, phase, context);
        List<Message> requestMessages = toolSchemas.isEmpty()
                ? List.copyOf(messages)
                : messagesWithToolInstructions(messages, toolSchemas);
        ModelRequest request = new ModelRequest(requestMessages, modelRef, phase.input(), toolSchemas, Map.of());
        ModelResponse response = structuredToolCalls(provider.complete(request, context));
        emit(context, state, phase.id(), AgentEventType.MODEL_INVOKED, Map.of("modelRef", String.valueOf(modelRef)));
        long prompt = response.usage().promptTokens();
        long completion = response.usage().completionTokens();
        state.cost = state.cost.add(prompt, completion, modelRef, providerName(response), estimatedCostUsd(response));
        emit(context, state, phase.id(), AgentEventType.TOKENS_CONSUMED,
                Map.of("promptTokens", prompt, "completionTokens", completion));
        return response;
    }

/**
 * Diagnostic formatter: keeps provider failures actionable while redacting credentials and bounding
 * the public error payload stored in run state or returned over DCP.
 */
    private String safeModelFailureMessage(String modelRef, RuntimeException ex) {
        String detail = sanitizeDiagnostic(throwableMessages(ex));
        if (detail.isBlank()) {
            detail = ex.getClass().getSimpleName();
        }
        return "Model provider failed for ref " + modelRef + ": " + detail;
    }

/**
 * Throwable walker: collects the exception chain so wrapped SDK errors expose the real provider
 * reason without including stack traces, prompts, or raw model output.
 */
    private String throwableMessages(Throwable throwable) {
        StringBuilder messages = new StringBuilder();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 6) {
            String message = current.getMessage();
            if (message == null || message.isBlank()) {
                message = current.getClass().getSimpleName();
            }
            if (!messages.isEmpty()) {
                messages.append(": ");
            }
            messages.append(message);
            current = current.getCause();
            depth++;
        }
        return messages.toString();
    }

/**
 * Redaction helper: removes common credential shapes from provider diagnostics before those
 * diagnostics become public run-state or DCP error messages.
 */
    private String sanitizeDiagnostic(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String redacted = value
                .replaceAll("AIza[0-9A-Za-z_\\-]{20,}", "<redacted>")
                .replaceAll("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s,;]+", "$1<redacted>")
                .replaceAll("(?i)(bearer\\s+)[A-Za-z0-9._~+/\\-=]+", "$1<redacted>")
                .replaceAll("(?i)((api[_-]?key|apikey|token|secret|password)\\s*[:=]\\s*)[^\\s,;]+",
                        "$1<redacted>");
        return redacted.length() <= 1000 ? redacted : redacted.substring(0, 1000) + "...";
    }

/**
 * Tool schema projector: exposes the phase's allowed Foundry tool names to provider adapters without leaking
 * concrete executor implementations or transport details out of the ToolRegistry port.
 */
    private List<Map<String, Object>> toolSchemas(AgentDefinition agent, AgentPhase phase, ExecutionContext context) {
        List<String> candidates = phase.allowedToolRefs().isEmpty() ? agent.toolRefs() : phase.allowedToolRefs();
        List<Map<String, Object>> schemas = new ArrayList<>();
        for (String toolName : candidates) {
            if (toolName == null || toolName.isBlank() || !toolRegistry.hasTool(toolName, context)) {
                continue;
            }
            schemas.add(Map.of(
                    "name", toolName,
                    "description", "Foundry registered tool: " + toolName,
                    "inputSchema", Map.of(
                            "type", "object",
                            "additionalProperties", true)));
        }
        return List.copyOf(schemas);
    }

/**
 * Prompt adapter: adds a provider-neutral textual tool-call contract for model adapters that do not support
 * native function-call blocks while still allowing adapters to consume ModelRequest.toolSchemas directly.
 */
    private List<Message> messagesWithToolInstructions(List<Message> messages, List<Map<String, Object>> toolSchemas) {
        List<Message> requestMessages = new ArrayList<>(messages);
        requestMessages.add(0, Message.system("""
                Foundry tools are available for this phase. To call a tool, respond with JSON only:
                {"toolCalls":[{"id":"stable-call-id","toolName":"<one of the allowed tools>","arguments":{}}]}
                After a TOOL message is returned, use that real tool result to produce the requested terminal JSON.
                Do not claim kind=execution unless a tool result was supplied.
                Allowed tools:
                %s
                """.formatted(toolSchemas)));
        return List.copyOf(requestMessages);
    }

/**
 * Serializer: converts tool outputs into provider-neutral JSON TOOL messages
 * instead of Java object text, preserving machine-readable tool result handoff.
 */
    private String toolResultContent(Map<String, Object> output) {
        try {
            return MAPPER.writeValueAsString(output == null ? Map.of() : output);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Tool result output is not JSON serializable", ex);
        }
    }

/**
 * Tool-call strategy: accepts a structured textual `toolCalls` envelope from providers without native tool
 * calling and converts it into the same ModelToolCall list used by native adapters.
 */
    private ModelResponse structuredToolCalls(ModelResponse response) {
        if (response == null || response.hasToolCalls() || response.message() == null) {
            return response;
        }
        List<ModelToolCall> calls = parseStructuredToolCalls(response.message().content());
        if (calls.isEmpty()) {
            return response;
        }
        return new ModelResponse(
                response.message(),
                calls,
                response.finishReason(),
                ModelTurnOutcome.TOOL_REQUESTED,
                response.usage(),
                response.metadata(),
                response.providerName(),
                response.estimatedCostUsd());
    }

/**
 * JSON parser: reads the optional top-level `toolCalls` array used by the provider-neutral fallback contract.
 */
    private List<ModelToolCall> parseStructuredToolCalls(String content) {
        java.util.Optional<Map<String, Object>> parsed = parseJsonObject(content);
        if (parsed.isEmpty()) {
            return List.of();
        }
        Object raw = parsed.get().get("toolCalls");
        if (!(raw instanceof List<?>)) {
            raw = parsed.get().get("tool_calls");
        }
        if (!(raw instanceof List<?> rawList)) {
            return List.of();
        }
        List<ModelToolCall> calls = new ArrayList<>();
        int index = 0;
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String toolName = stringValue(map.get("toolName"));
            if (toolName.isBlank()) {
                toolName = stringValue(map.get("name"));
            }
            if (toolName.isBlank()) {
                continue;
            }
            String id = stringValue(map.get("id"));
            if (id.isBlank()) {
                id = "tool-call-" + (++index);
            }
            Object arguments = map.get("arguments");
            Map<?, ?> argumentMap = arguments instanceof Map<?, ?> current
                    ? current
                    : map.get("args") instanceof Map<?, ?> next ? next : Map.of();
            Map<String, Object> normalizedArguments = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : argumentMap.entrySet()) {
                normalizedArguments.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            calls.add(new ModelToolCall(id, toolName, normalizedArguments));
        }
        return List.copyOf(calls);
    }

/**
 * Text helper: normalizes nullable JSON scalar values for structured tool-call parsing.
 */
    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

/**
 * Implements the providerName helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private String providerName(ModelResponse response) {
        if (response.providerName() != null && !response.providerName().isBlank()) {
            return response.providerName();
        }
        Object value = response.metadata().get("providerName");
        return value == null ? null : String.valueOf(value);
    }

/**
 * Implements the estimatedCostUsd helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private java.math.BigDecimal estimatedCostUsd(ModelResponse response) {
        if (response.estimatedCostUsd().signum() > 0) {
            return response.estimatedCostUsd();
        }
        Object value = response.metadata().getOrDefault("estimatedCostUsd", response.metadata().get("costUsd"));
        if (value instanceof java.math.BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return java.math.BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String string && !string.isBlank()) {
            return new java.math.BigDecimal(string);
        }
        return java.math.BigDecimal.ZERO;
    }

/**
 * Performs the assemble operation for this component, translating validated inputs into the domain result expected by callers.
 */
    private List<Message> assemble(AgentPhase phase, Map<String, Object> resolvedInput) {
        if (phase.promptTemplateRef() != null && templates.containsKey(phase.promptTemplateRef())) {
            return new ArrayList<>(promptAssembler.assemble(templates.get(phase.promptTemplateRef()), resolvedInput));
        }
        List<Message> messages = new ArrayList<>();
        Object prompt = resolvedInput.get("prompt");
        if (prompt != null) {
            messages.add(Message.user(String.valueOf(prompt)));
        }
        return messages;
    }

/**
 * Implements the joinChunks helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private String joinChunks(RagResult rag) {
        StringBuilder sb = new StringBuilder("Context:\n");
        for (Chunk chunk : rag.chunks()) {
            sb.append("- ").append(chunk.text()).append('\n');
        }
        return sb.toString();
    }

    // --- scheduling helpers ---

/**
 * Confirms that all dependency and inbound-edge predecessors reached terminal states before a phase is eligible to run.
 */
    private boolean predecessorsTerminal(State state, AgentPhase phase) {
        for (String dependency : phase.dependencies()) {
            if (!isTerminal(statusOf(state, dependency))) {
                return false;
            }
        }
        for (EdgeDefinition edge : inboundEdges(state.agent, phase)) {
            if (!isTerminal(statusOf(state, edge.from()))) {
                return false;
            }
        }
        return true;
    }

/**
 * Performs the isEdgeGated operation for this component, translating validated inputs into the domain result expected by callers.
 */
    private boolean isEdgeGated(AgentDefinition agent, AgentPhase phase) {
        return !inboundEdges(agent, phase).isEmpty();
    }

/**
 * Implements the anyInboundEdgeSatisfied helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private boolean anyInboundEdgeSatisfied(State state, AgentPhase phase) {
        for (EdgeDefinition edge : inboundEdges(state.agent, phase)) {
            if (statusOf(state, edge.from()) == AgentPhaseStatus.COMPLETED && evaluateWhen(edge.when(), state)) {
                return true;
            }
        }
        return false;
    }

/**
 * Implements the evaluateWhen helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private boolean evaluateWhen(ConditionDefinition when, State state) {
        if (when == null || when.expression() == null) {
            return true;
        }
        String expression = when.expression().trim();
        Comparison comparison = parseComparison(expression);
        if (comparison != null) {
            Object left = resolveConditionOperand(comparison.left(), state);
            Object right = resolveConditionOperand(comparison.right(), state);
            boolean matches = Objects.equals(normalizeComparable(left), normalizeComparable(right));
            return comparison.negated() ? !matches : matches;
        }
        Object value = resolver.resolve(expression, state.agentInput, phaseOutputs(state)).orElse(null);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            String normalized = s.trim();
            if (normalized.equalsIgnoreCase("true")) {
                return true;
            }
            if (normalized.equalsIgnoreCase("false")) {
                return false;
            }
            return !normalized.isBlank();
        }
        if (value instanceof Collection<?> c) {
            return !c.isEmpty();
        }
        return value != null;
    }

/**
 * Parser: recognizes the intentionally small equality subset used by agent DAG routing.
 */
    private Comparison parseComparison(String expression) {
        int equals = expression.indexOf("==");
        if (equals >= 0) {
            return new Comparison(expression.substring(0, equals), expression.substring(equals + 2), false);
        }
        int notEquals = expression.indexOf("!=");
        if (notEquals >= 0) {
            return new Comparison(expression.substring(0, notEquals), expression.substring(notEquals + 2), true);
        }
        return null;
    }

/**
 * Strategy: resolves a comparison operand as either a data reference or a literal value.
 */
    private Object resolveConditionOperand(String operand, State state) {
        String trimmed = operand == null ? "" : operand.trim();
        if (trimmed.startsWith("$.")) {
            return resolver.resolve(trimmed, state.agentInput, phaseOutputs(state)).orElse(null);
        }
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        if (trimmed.equalsIgnoreCase("true")) {
            return true;
        }
        if (trimmed.equalsIgnoreCase("false")) {
            return false;
        }
        return trimmed;
    }

/**
 * Adapter: compares condition operands in the same simple scalar space used by YAML/JSON phase outputs.
 */
    private Object normalizeComparable(Object value) {
        return value instanceof String text ? text.trim() : value;
    }

/**
 * Performs the isToolAllowed operation for this component, translating validated inputs into the domain result expected by callers.
 */
    private boolean isToolAllowed(AgentDefinition agent, AgentPhase phase, String toolName) {
        if (!agent.toolRefs().isEmpty() && !agent.toolRefs().contains(toolName)) {
            return false;
        }
        return phase.allowedToolRefs().isEmpty() || phase.allowedToolRefs().contains(toolName);
    }

/**
 * Implements the inboundEdges helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private List<EdgeDefinition> inboundEdges(AgentDefinition agent, AgentPhase phase) {
        List<EdgeDefinition> inbound = new ArrayList<>();
        for (EdgeDefinition edge : agent.edges()) {
            if (edge.to().equals(phase.id())) {
                inbound.add(edge);
            }
        }
        return inbound;
    }

/**
 * Implements the statusOf helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private AgentPhaseStatus statusOf(State state, String phaseId) {
        AgentPhaseState ps = state.phases.get(phaseId);
        return ps == null ? AgentPhaseStatus.PENDING : ps.status();
    }

/**
 * Performs the isTerminal operation for this component, translating validated inputs into the domain result expected by callers.
 */
    private boolean isTerminal(AgentPhaseStatus status) {
        return status == AgentPhaseStatus.COMPLETED || status == AgentPhaseStatus.SKIPPED
                || status == AgentPhaseStatus.FAILED || status == AgentPhaseStatus.CANCELLED;
    }

/**
 * Implements the phaseOutputs helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private Map<String, Map<String, Object>> phaseOutputs(State state) {
        Map<String, Map<String, Object>> outputs = new LinkedHashMap<>();
        state.phases.forEach((id, ps) -> {
            if (ps.status() == AgentPhaseStatus.COMPLETED) {
                outputs.put(id, ps.output());
            }
        });
        return outputs;
    }

/**
 * Implements the markPhase helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private void markPhase(State state, String phaseId, AgentPhaseState newState) {
        state.phases.put(phaseId, newState);
    }

/**
 * Implements the skipped helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private AgentPhaseState skipped(String phaseId) {
        Instant now = Instant.now();
        return new AgentPhaseState(phaseId, AgentPhaseStatus.SKIPPED, Map.of(), List.of(), List.of(),
                Map.of(), null, null, now, now);
    }

/**
 * Implements the failedPhase helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private AgentPhaseState failedPhase(String phaseId, Map<String, Object> input, List<Message> messages,
                                        String errorCode, String errorMessage, Instant started) {
        return new AgentPhaseState(phaseId, AgentPhaseStatus.FAILED, input, List.copyOf(messages), List.of(),
                Map.of(), errorCode, errorMessage, started, Instant.now());
    }

    /**
     * Failure projector: preserves completed tool-call evidence and canonical partial output when a
     * tool reports an expected structured failure.
     */
    private AgentPhaseState failedToolPhase(
            String phaseId,
            Map<String, Object> input,
            List<Message> messages,
            List<ToolCall> toolCalls,
            ToolCallResult result,
            Instant started) {
        return new AgentPhaseState(
                phaseId,
                AgentPhaseStatus.FAILED,
                input,
                List.copyOf(messages),
                List.copyOf(toolCalls),
                result.failure().partialOutput(),
                result.failure().code(),
                result.failure().message(),
                started,
                Instant.now());
    }

    // --- run-state assembly ---

/**
 * Persists a run-state checkpoint without an error payload, preserving the overload used by successful scheduler paths.
 */
    private AgentRunState save(State state, AgentRunStatus status, ExecutionContext context) {
        return save(state, status, context, null, null);
    }

/**
 * Performs the save operation for this component, translating validated inputs into the domain result expected by callers.
 */
    private AgentRunState save(State state, AgentRunStatus status, ExecutionContext context,
                              String errorCode, String errorMessage) {
        AgentRunState run = new AgentRunState(state.tenantId, state.runId, state.agent.id(), state.agent.version(),
                status, state.agentInput, new LinkedHashMap<>(state.phases), state.cost, errorCode, errorMessage,
                state.createdAt, Instant.now());
        store.save(run, context);
        return run;
    }

/**
 * Implements the withStatus helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private AgentRunState withStatus(AgentRunState run, Map<String, AgentPhaseState> phases,
                                     AgentRunStatus status, String errorCode, String errorMessage) {
        return new AgentRunState(run.tenantId(), run.runId(), run.agentId(), run.agentVersion(), status,
                run.agentInput(), phases, run.cost(), errorCode, errorMessage, run.createdAt(), Instant.now());
    }

    // --- events ---

/**
 * Publishes a phase-scoped lifecycle event with tenant, user, correlation, and payload context attached.
 */
    private void emit(ExecutionContext context, State state, String phaseId, AgentEventType type, Map<String, Object> payload) {
        eventSink.publish(new AgentEvent(UUID.randomUUID().toString(), Instant.now(), state.agent.id(), state.runId,
                phaseId, state.tenantId, context == null ? null : context.userId(),
                context == null ? null : context.correlationId(), null, type, payload), context);
    }

/**
 * Performs the emitRun operation for this component, translating validated inputs into the domain result expected by callers.
 */
    private void emitRun(ExecutionContext context, AgentRunState run, AgentEventType type, Map<String, Object> payload) {
        eventSink.publish(new AgentEvent(UUID.randomUUID().toString(), Instant.now(), run.agentId(), run.runId(),
                null, run.tenantId(), context == null ? null : context.userId(),
                context == null ? null : context.correlationId(), null, type, payload), context);
    }

    /** Value object: parsed equality or inequality route condition. */
    private record Comparison(String left, String right, boolean negated) {
    }

    /** Value object: model-provider call success or the structured phase failure replacing a provider exception. */
    private record ModelCallOutcome(ModelResponse response, AgentPhaseState failure) {
        static ModelCallOutcome success(ModelResponse response) {
            return new ModelCallOutcome(response, null);
        }

        static ModelCallOutcome failure(AgentPhaseState failure) {
            return new ModelCallOutcome(null, failure);
        }

        boolean success() {
            return failure == null;
        }
    }

    /** Value Object: carries either validated mapped output or correction-safe issues. */
    private record OutputValidation(Map<String, Object> output, List<ValidationIssue> issues) {
        /** Factory: creates a successful immutable output result. */
        static OutputValidation valid(Map<String, Object> output) {
            return new OutputValidation(Map.copyOf(output), List.of());
        }

        /** Factory: creates an invalid result with at least one issue. */
        static OutputValidation invalid(List<ValidationIssue> issues) {
            return new OutputValidation(Map.of(), List.copyOf(issues));
        }

        /** Predicate: reports whether validation produced mapped output. */
        boolean valid() {
            return issues.isEmpty();
        }
    }

    /** Value object: result of building a structured phase output or a structured mapping failure. */
    private record PhaseOutput(
            boolean success,
            Map<String, Object> values,
            String errorCode,
            String errorMessage
    ) {
        static PhaseOutput success(Map<String, Object> values) {
            return new PhaseOutput(true, Map.copyOf(values), null, null);
        }

        static PhaseOutput failure(String errorCode, String errorMessage) {
            return new PhaseOutput(false, Map.of(), errorCode, errorMessage);
        }
    }

    /** Value object: result of resolving one outputMapping expression or nested mapping value. */
    private record MappingResolution(boolean resolved, Object value) {
        static MappingResolution success(Object value) {
            return new MappingResolution(true, value);
        }

        static MappingResolution unresolved() {
            return new MappingResolution(false, null);
        }
    }

    /** Mutable working state threaded through a single run. */
/**
 * class for the Foundry AI substrate surface; documents the State contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
    private static final class State {
        final String tenantId;
        final String runId;
        final AgentDefinition agent;
        final Map<String, Object> agentInput;
        final Map<String, AgentPhaseState> phases;
        CostAccounting cost;
        final Instant createdAt;

        State(String tenantId, String runId, AgentDefinition agent, Map<String, Object> agentInput,
              Map<String, AgentPhaseState> phases, CostAccounting cost, Instant createdAt) {
            this.tenantId = tenantId;
            this.runId = runId;
            this.agent = agent;
            this.agentInput = agentInput;
            this.phases = phases;
            this.cost = cost;
            this.createdAt = createdAt;
        }
    }
}
