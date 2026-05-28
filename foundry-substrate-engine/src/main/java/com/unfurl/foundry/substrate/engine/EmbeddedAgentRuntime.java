package com.unfurl.foundry.substrate.engine;

import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.agent.AgentDefinitionValidator;
import com.unfurl.foundry.substrate.agent.AgentPhase;
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
import com.unfurl.foundry.substrate.ports.AgentEventSink;
import com.unfurl.foundry.substrate.ports.AgentRuntime;
import com.unfurl.foundry.substrate.ports.ModelProvider;
import com.unfurl.foundry.substrate.ports.ProviderRegistry;
import com.unfurl.foundry.substrate.ports.RagRetriever;
import com.unfurl.foundry.substrate.ports.ToolCallRequest;
import com.unfurl.foundry.substrate.ports.ToolCallResult;
import com.unfurl.foundry.substrate.ports.ToolExecutor;
import com.unfurl.foundry.substrate.ports.ToolRegistry;
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
import java.util.UUID;

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
    private final ProviderRegistry providerRegistry;
    private final ToolRegistry toolRegistry;
    private final RagRetriever ragRetriever;
    private final CostGuardrail costGuardrail;
    private final PermissionBridge permissionBridge;
    private final AgentEventSink eventSink;
    private final PromptAssembler promptAssembler;
    private final DataReferenceResolver resolver;
    private final AgentDefinitionValidator validator;
    private final AgentRunStore store;
    private final Map<String, PromptTemplate> templates;

    public EmbeddedAgentRuntime(ProviderRegistry providerRegistry, ToolRegistry toolRegistry) {
        this(providerRegistry, toolRegistry, null, new BudgetPolicyCostGuardrail(), new AllowAllPermissionBridge(),
                new NoopAgentEventSink(), new PromptAssembler(), new DataReferenceResolver(),
                new AgentDefinitionValidator(), new InMemoryAgentRunStore(), Map.of());
    }

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
        this.providerRegistry = providerRegistry;
        this.toolRegistry = toolRegistry;
        this.ragRetriever = ragRetriever;
        this.costGuardrail = costGuardrail;
        this.permissionBridge = permissionBridge;
        this.eventSink = eventSink;
        this.promptAssembler = promptAssembler;
        this.resolver = resolver;
        this.validator = validator;
        this.store = store;
        this.templates = Map.copyOf(templates);
    }

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

    @Override
    public AgentRunState resume(String runId, Map<String, Object> signal, ExecutionContext context) {
        AgentRunState loaded = store.load(runId, context)
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
        // The in-memory runner has no suspend point in this slice, so resume reloads and
        // returns. Durable suspend/resume is unfurl-foundry's responsibility.
        return loaded;
    }

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
        ModelResponse response = callModel(state, phase, provider, messages, modelRef, context);
        messages.add(response.message() == null ? Message.assistant("") : response.message());

        int iterations = 0;
        while (response.hasToolCalls() && iterations < phase.maxToolIterations()) {
            for (ModelToolCall call : response.toolCalls()) {
                if (!isToolAllowed(state.agent, phase, call.toolName())) {
                    return failedPhase(phase.id(), resolvedInput, messages, "TOOL_NOT_ALLOWED",
                            "Tool not allowed for phase: " + call.toolName(), started);
                }
                PermissionDecision permission = permissionBridge.check(call.toolName(), call.arguments(), context);
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
                ToolCallResult toolResult = executor.execute(
                        new ToolCallRequest(call.id(), call.toolName(), call.arguments(), Map.of()), context);
                toolCalls.add(new ToolCall(call.id(), call.toolName(), call.arguments(),
                        toolResult.output(), toolResult.errorCode(), toolResult.errorMessage(), toolStart, Instant.now()));
                if (!toolResult.success()) {
                    emit(context, state, phase.id(), AgentEventType.TOOL_FAILED, Map.of("tool", call.toolName()));
                    return failedPhase(phase.id(), resolvedInput, messages, "TOOL_FAILED",
                            "Tool failed: " + call.toolName(), started);
                }
                emit(context, state, phase.id(), AgentEventType.TOOL_COMPLETED, Map.of("tool", call.toolName()));
                messages.add(Message.tool(call.id(), String.valueOf(toolResult.output())));
            }
            iterations++;
            response = callModel(state, phase, provider, messages, modelRef, context);
            messages.add(response.message() == null ? Message.assistant("") : response.message());
        }
        if (response.hasToolCalls()) {
            return failedPhase(phase.id(), resolvedInput, messages, "MAX_TOOL_ITERATIONS_EXCEEDED",
                    "Model requested tool calls after " + phase.maxToolIterations() + " iterations", started);
        }

        String content = response.message() == null ? "" : response.message().content();
        Map<String, Object> output = Map.of("content", content == null ? "" : content);
        return new AgentPhaseState(phase.id(), AgentPhaseStatus.COMPLETED, resolvedInput, List.copyOf(messages),
                List.copyOf(toolCalls), output, null, null, started, Instant.now());
    }

    private ModelResponse callModel(State state, AgentPhase phase, ModelProvider provider, List<Message> messages,
                                    String modelRef, ExecutionContext context) {
        ModelRequest request = new ModelRequest(List.copyOf(messages), modelRef, phase.input(), List.of(), Map.of());
        ModelResponse response = provider.complete(request, context);
        emit(context, state, phase.id(), AgentEventType.MODEL_INVOKED, Map.of("modelRef", String.valueOf(modelRef)));
        long prompt = response.usage().promptTokens();
        long completion = response.usage().completionTokens();
        state.cost = state.cost.add(prompt, completion, modelRef, providerName(response), estimatedCostUsd(response));
        emit(context, state, phase.id(), AgentEventType.TOKENS_CONSUMED,
                Map.of("promptTokens", prompt, "completionTokens", completion));
        return response;
    }

    private String providerName(ModelResponse response) {
        if (response.providerName() != null && !response.providerName().isBlank()) {
            return response.providerName();
        }
        Object value = response.metadata().get("providerName");
        return value == null ? null : String.valueOf(value);
    }

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

    private String joinChunks(RagResult rag) {
        StringBuilder sb = new StringBuilder("Context:\n");
        for (Chunk chunk : rag.chunks()) {
            sb.append("- ").append(chunk.text()).append('\n');
        }
        return sb.toString();
    }

    // --- scheduling helpers ---

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

    private boolean isEdgeGated(AgentDefinition agent, AgentPhase phase) {
        return !inboundEdges(agent, phase).isEmpty();
    }

    private boolean anyInboundEdgeSatisfied(State state, AgentPhase phase) {
        for (EdgeDefinition edge : inboundEdges(state.agent, phase)) {
            if (statusOf(state, edge.from()) == AgentPhaseStatus.COMPLETED && evaluateWhen(edge.when(), state)) {
                return true;
            }
        }
        return false;
    }

    private boolean evaluateWhen(ConditionDefinition when, State state) {
        if (when == null || when.expression() == null) {
            return true;
        }
        Object value = resolver.resolve(when.expression(), state.agentInput, phaseOutputs(state)).orElse(null);
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

    private boolean isToolAllowed(AgentDefinition agent, AgentPhase phase, String toolName) {
        if (!agent.toolRefs().isEmpty() && !agent.toolRefs().contains(toolName)) {
            return false;
        }
        return phase.allowedToolRefs().isEmpty() || phase.allowedToolRefs().contains(toolName);
    }

    private List<EdgeDefinition> inboundEdges(AgentDefinition agent, AgentPhase phase) {
        List<EdgeDefinition> inbound = new ArrayList<>();
        for (EdgeDefinition edge : agent.edges()) {
            if (edge.to().equals(phase.id())) {
                inbound.add(edge);
            }
        }
        return inbound;
    }

    private AgentPhaseStatus statusOf(State state, String phaseId) {
        AgentPhaseState ps = state.phases.get(phaseId);
        return ps == null ? AgentPhaseStatus.PENDING : ps.status();
    }

    private boolean isTerminal(AgentPhaseStatus status) {
        return status == AgentPhaseStatus.COMPLETED || status == AgentPhaseStatus.SKIPPED
                || status == AgentPhaseStatus.FAILED || status == AgentPhaseStatus.CANCELLED;
    }

    private Map<String, Map<String, Object>> phaseOutputs(State state) {
        Map<String, Map<String, Object>> outputs = new LinkedHashMap<>();
        state.phases.forEach((id, ps) -> {
            if (ps.status() == AgentPhaseStatus.COMPLETED) {
                outputs.put(id, ps.output());
            }
        });
        return outputs;
    }

    private void markPhase(State state, String phaseId, AgentPhaseState newState) {
        state.phases.put(phaseId, newState);
    }

    private AgentPhaseState skipped(String phaseId) {
        Instant now = Instant.now();
        return new AgentPhaseState(phaseId, AgentPhaseStatus.SKIPPED, Map.of(), List.of(), List.of(),
                Map.of(), null, null, now, now);
    }

    private AgentPhaseState failedPhase(String phaseId, Map<String, Object> input, List<Message> messages,
                                        String errorCode, String errorMessage, Instant started) {
        return new AgentPhaseState(phaseId, AgentPhaseStatus.FAILED, input, List.copyOf(messages), List.of(),
                Map.of(), errorCode, errorMessage, started, Instant.now());
    }

    // --- run-state assembly ---

    private AgentRunState save(State state, AgentRunStatus status, ExecutionContext context) {
        return save(state, status, context, null, null);
    }

    private AgentRunState save(State state, AgentRunStatus status, ExecutionContext context,
                              String errorCode, String errorMessage) {
        AgentRunState run = new AgentRunState(state.tenantId, state.runId, state.agent.id(), state.agent.version(),
                status, state.agentInput, new LinkedHashMap<>(state.phases), state.cost, errorCode, errorMessage,
                state.createdAt, Instant.now());
        store.save(run, context);
        return run;
    }

    private AgentRunState withStatus(AgentRunState run, Map<String, AgentPhaseState> phases,
                                     AgentRunStatus status, String errorCode, String errorMessage) {
        return new AgentRunState(run.tenantId(), run.runId(), run.agentId(), run.agentVersion(), status,
                run.agentInput(), phases, run.cost(), errorCode, errorMessage, run.createdAt(), Instant.now());
    }

    // --- events ---

    private void emit(ExecutionContext context, State state, String phaseId, AgentEventType type, Map<String, Object> payload) {
        eventSink.publish(new AgentEvent(UUID.randomUUID().toString(), Instant.now(), state.agent.id(), state.runId,
                phaseId, state.tenantId, context == null ? null : context.userId(),
                context == null ? null : context.correlationId(), null, type, payload), context);
    }

    private void emitRun(ExecutionContext context, AgentRunState run, AgentEventType type, Map<String, Object> payload) {
        eventSink.publish(new AgentEvent(UUID.randomUUID().toString(), Instant.now(), run.agentId(), run.runId(),
                null, run.tenantId(), context == null ? null : context.userId(),
                context == null ? null : context.correlationId(), null, type, payload), context);
    }

    /** Mutable working state threaded through a single run. */
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
