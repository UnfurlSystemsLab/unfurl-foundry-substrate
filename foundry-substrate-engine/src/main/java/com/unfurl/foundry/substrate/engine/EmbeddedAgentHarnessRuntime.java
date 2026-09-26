package com.unfurl.foundry.substrate.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.agent.AgentHarnessDefinition;
import com.unfurl.foundry.substrate.agent.AgentHarnessDefinitionValidator;
import com.unfurl.foundry.substrate.agent.AgentHarnessLoopPolicy;
import com.unfurl.foundry.substrate.agent.AgentPhase;
import com.unfurl.foundry.substrate.ports.AgentHarnessRuntime;
import com.unfurl.foundry.substrate.ports.AgentRuntime;
import com.unfurl.foundry.substrate.runstate.AgentHarnessObservation;
import com.unfurl.foundry.substrate.runstate.AgentHarnessRunState;
import com.unfurl.foundry.substrate.runstate.AgentHarnessStatus;
import com.unfurl.foundry.substrate.runstate.AgentPhaseState;
import com.unfurl.foundry.substrate.runstate.AgentPhaseStatus;
import com.unfurl.foundry.substrate.runstate.AgentRunState;
import com.unfurl.foundry.substrate.runstate.AgentRunStatus;
import com.unfurl.foundry.substrate.terminal.AgentTerminalEnvelope;
import com.unfurl.foundry.substrate.terminal.AgentTerminalStatus;
import com.unfurl.foundry.substrate.terminal.TerminalEnvelopeNormalizer;
import com.unfurl.substrate.policy.ExecutionContext;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Ports & Adapters implementation: a sequential in-memory harness around an
 * injected {@link AgentRuntime}. It gives embedded products a bounded agentic
 * loop without adding durability, scheduling, provider SDKs, or server APIs to
 * the substrate.
 */
public final class EmbeddedAgentHarnessRuntime implements AgentHarnessRuntime {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AgentRuntime agentRuntime;
    private final AgentHarnessDefinitionValidator validator;
    private final TerminalEnvelopeNormalizer terminalNormalizer;
    private final ConcurrentMap<String, AgentHarnessRunState> runs = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AgentHarnessDefinition> definitions = new ConcurrentHashMap<>();

    /**
     * Constructs EmbeddedAgentHarnessRuntime around the supplied agent runtime
     * and the standard harness validator.
     */
    public EmbeddedAgentHarnessRuntime(AgentRuntime agentRuntime) {
        this(agentRuntime, new AgentHarnessDefinitionValidator(), new TerminalEnvelopeNormalizer());
    }

    /**
     * Constructs EmbeddedAgentHarnessRuntime with injected validation so hosts
     * can reuse their already configured agent validation strategy.
     */
    public EmbeddedAgentHarnessRuntime(AgentRuntime agentRuntime, AgentHarnessDefinitionValidator validator) {
        this(agentRuntime, validator, new TerminalEnvelopeNormalizer());
    }

    /**
     * Constructs the harness with injected validation and terminal normalization strategies.
     */
    public EmbeddedAgentHarnessRuntime(
            AgentRuntime agentRuntime,
            AgentHarnessDefinitionValidator validator,
            TerminalEnvelopeNormalizer terminalNormalizer) {
        this.agentRuntime = Objects.requireNonNull(agentRuntime, "agentRuntime");
        this.validator = validator == null ? new AgentHarnessDefinitionValidator() : validator;
        this.terminalNormalizer = terminalNormalizer == null ? new TerminalEnvelopeNormalizer() : terminalNormalizer;
    }

    /**
     * Starts a bounded harness run and returns the terminal, waiting, gap, or
     * failure snapshot produced by the in-memory loop.
     */
    @Override
    public AgentHarnessRunState start(AgentHarnessDefinition harness, Map<String, Object> input, ExecutionContext context) {
        validator.validate(harness);
        String runId = UUID.randomUUID().toString();
        Instant createdAt = Instant.now();
        Map<String, Object> originalInput = input == null ? Map.of() : Map.copyOf(input);
        definitions.put(runId, harness);
        return runLoop(harness, runId, createdAt, originalInput, originalInput, List.of(), 0, context);
    }

    /**
     * Resumes a waiting in-memory harness by merging the external signal under
     * the stable {@code signal} input key and running the remaining turn budget.
     */
    @Override
    public AgentHarnessRunState resume(String runId, Map<String, Object> signal, ExecutionContext context) {
        AgentHarnessRunState current = requireRun(runId);
        if (current.status() != AgentHarnessStatus.WAITING_FOR_USER
                && current.status() != AgentHarnessStatus.WAITING_FOR_APPROVAL) {
            return current;
        }
        AgentHarnessDefinition harness = definitions.get(runId);
        if (harness == null) {
            return save(withStatus(current, AgentHarnessStatus.FAILED, current.output(),
                    "HARNESS_DEFINITION_MISSING", "Harness definition was not retained for run: " + runId));
        }
        Map<String, Object> nextInput = new LinkedHashMap<>(current.latestInput());
        nextInput.put("signal", signal == null ? Map.of() : Map.copyOf(signal));
        return runLoop(harness, runId, current.createdAt(), current.originalInput(), Map.copyOf(nextInput),
                current.observations(), current.turn(), context);
    }

    /**
     * Cancels a known in-memory harness run without trying to cancel already
     * completed inner agent runs.
     */
    @Override
    public AgentHarnessRunState cancel(String runId, ExecutionContext context) {
        AgentHarnessRunState current = requireRun(runId);
        if (isTerminal(current.status())) {
            return current;
        }
        return save(withStatus(current, AgentHarnessStatus.CANCELLED, current.output(), null, null));
    }

    /**
     * Template Method: executes bounded agent turns until the harness reaches a
     * terminal decision or exhausts its loop policy.
     */
    private AgentHarnessRunState runLoop(
            AgentHarnessDefinition harness,
            String runId,
            Instant createdAt,
            Map<String, Object> originalInput,
            Map<String, Object> latestInput,
            List<AgentHarnessObservation> existingObservations,
            int completedTurns,
            ExecutionContext context
    ) {
        AgentHarnessLoopPolicy policy = harness.loopPolicy();
        List<AgentHarnessObservation> observations = new ArrayList<>(existingObservations);
        Map<String, Object> currentInput = latestInput == null ? Map.of() : Map.copyOf(latestInput);
        save(snapshot(context, harness, runId, AgentHarnessStatus.RUNNING, completedTurns, createdAt, originalInput,
                currentInput, observations, Map.of(), null, null));

        int turn = completedTurns;
        while (turn < policy.maxTurns()) {
            if (deadlineExceeded(createdAt, policy)) {
                return save(snapshot(context, harness, runId, AgentHarnessStatus.FAILED, turn, createdAt, originalInput,
                        currentInput, observations, Map.of(), "HARNESS_DEADLINE_EXCEEDED",
                        "Harness deadline exceeded after " + policy.maxDurationMillis() + " ms"));
            }

            turn++;
            Instant turnStartedAt = Instant.now();
            AgentRunState agentRun = agentRuntime.start(harness.agent(), currentInput, context);
            Map<String, Object> output = terminalOutput(harness.agent(), agentRun);
            Decision decision = decide(agentRun, output);
            observations.add(new AgentHarnessObservation(turn, agentRun.runId(), agentRun.status(), output,
                    decision.kind(), decision.message(), turnStartedAt, Instant.now(),
                    envelopeFor(decision.status(), output, decision.errorCode(), decision.message())));

            if (decision.status() == AgentHarnessStatus.COMPLETED
                    || decision.status() == AgentHarnessStatus.WAITING_FOR_USER
                    || decision.status() == AgentHarnessStatus.WAITING_FOR_APPROVAL
                    || decision.status() == AgentHarnessStatus.ESCALATED
                    || decision.status() == AgentHarnessStatus.GAP
                    || decision.status() == AgentHarnessStatus.FAILED
                    || decision.status() == AgentHarnessStatus.CANCELLED) {
                return save(snapshot(context, harness, runId, decision.status(), turn, createdAt, originalInput,
                        currentInput, observations, output, decision.errorCode(), decision.message()));
            }

            currentInput = decision.nextInput();
            save(snapshot(context, harness, runId, AgentHarnessStatus.RUNNING, turn, createdAt, originalInput,
                    currentInput, observations, output, null, null));
        }

        return save(snapshot(context, harness, runId, AgentHarnessStatus.FAILED, turn, createdAt, originalInput,
                currentInput, observations, Map.of(), "HARNESS_MAX_TURNS_EXCEEDED",
                "Harness exhausted maxTurns=" + policy.maxTurns()));
    }

    /**
     * Selector: chooses the structured terminal output for a completed inner
     * agent run using terminal phase/kind metadata when present.
     */
    private Map<String, Object> terminalOutput(AgentDefinition agent, AgentRunState run) {
        List<String> terminalPhaseIds = terminalPhaseIds(agent.metadata());
        List<String> terminalKinds = terminalKinds(agent.metadata());
        Map<String, Object> fallback = Map.of("content", "");
        for (int i = agent.phases().size() - 1; i >= 0; i--) {
            AgentPhase phaseDefinition = agent.phases().get(i);
            AgentPhaseState phase = run.phases().get(phaseDefinition.id());
            if (phase == null || phase.status() != AgentPhaseStatus.COMPLETED) {
                continue;
            }
            Map<String, Object> output = normalizeTerminalOutput(phase.output());
            fallback = output;
            if (!terminalPhaseIds.isEmpty() && !terminalPhaseIds.contains(phaseDefinition.id())) {
                continue;
            }
            if (terminalKinds.isEmpty()) {
                return output;
            }
            Object kind = output.get("kind");
            if (kind != null && terminalKinds.contains(String.valueOf(kind))) {
                return output;
            }
        }
        return fallback;
    }

    /**
     * Adapter: normalizes raw content-only phase output into a structured map
     * when the model returned JSON inside the content field.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeTerminalOutput(Map<String, Object> output) {
        if (output == null || output.isEmpty()) {
            return Map.of("content", "");
        }
        if (output.containsKey("kind") || output.size() > 1) {
            return output;
        }
        Object rawContent = output.get("content");
        String content = rawContent == null ? "" : String.valueOf(rawContent);
        if (content.isBlank()) {
            return Map.of("content", "");
        }
        try {
            Object parsed = MAPPER.readValue(extractJson(content), Object.class);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
                return Map.copyOf(normalized);
            }
        } catch (Exception ignored) {
            // Free-form model output remains available through the content key.
        }
        return Map.of("content", content);
    }

    /**
     * Strategy: interprets a terminal agent output as the next harness decision.
     */
    private Decision decide(AgentRunState agentRun, Map<String, Object> output) {
        if (agentRun.status() == AgentRunStatus.FAILED) {
            return Decision.failure("failed", agentRun.errorCode() == null ? "AGENT_FAILED" : agentRun.errorCode(),
                    agentRun.errorMessage() == null ? "Inner agent failed" : agentRun.errorMessage());
        }
        if (agentRun.status() == AgentRunStatus.CANCELLED) {
            return new Decision(AgentHarnessStatus.CANCELLED, "cancelled", Map.of(), null, "Inner agent cancelled");
        }
        if (agentRun.status() != AgentRunStatus.COMPLETED) {
            return Decision.failure("failed", "AGENT_NOT_COMPLETED", "Inner agent ended in " + agentRun.status());
        }

        String kind = string(output.get("kind")).trim();
        if ("continue".equalsIgnoreCase(kind)) {
            Object nextInput = output.get("nextInput");
            if (!(nextInput instanceof Map<?, ?> map)) {
                return Decision.failure("continue", "HARNESS_CONTINUE_INPUT_MISSING",
                        "Harness continue output must include object-valued nextInput");
            }
            return new Decision(AgentHarnessStatus.RUNNING, "continue", stringifyKeys(map), null,
                    messageFrom(output, "Agent requested another harness turn"));
        }
        AgentTerminalEnvelope envelope = terminalNormalizer.normalize(output);
        AgentHarnessStatus status = switch (envelope.status()) {
            case COMPLETED -> AgentHarnessStatus.COMPLETED;
            case WAITING_FOR_USER -> AgentHarnessStatus.WAITING_FOR_USER;
            case WAITING_FOR_APPROVAL -> AgentHarnessStatus.WAITING_FOR_APPROVAL;
            case ESCALATED -> AgentHarnessStatus.ESCALATED;
            case GAP -> AgentHarnessStatus.GAP;
            case FAILED -> AgentHarnessStatus.FAILED;
            case CANCELLED -> AgentHarnessStatus.CANCELLED;
        };
        String errorCode = envelope.error() == null ? null : envelope.error().code();
        String message = envelope.error() == null
                ? messageFrom(output, "Agent completed")
                : envelope.error().message();
        return new Decision(status, kind.isBlank() ? "complete" : kind, Map.of(), errorCode, message);
    }

    /**
     * Projection Strategy: creates the canonical terminal envelope stored with observations and snapshots.
     */
    private AgentTerminalEnvelope envelopeFor(
            AgentHarnessStatus status,
            Map<String, Object> output,
            String errorCode,
            String errorMessage) {
        if (status == AgentHarnessStatus.RUNNING) {
            return null;
        }
        if (status == AgentHarnessStatus.FAILED) {
            return terminalNormalizer.failure(errorCode, errorMessage, output);
        }
        Map<String, Object> normalized = new LinkedHashMap<>(output == null ? Map.of() : output);
        if (status != AgentHarnessStatus.COMPLETED || !normalized.containsKey("kind")) {
            String kind = switch (status) {
                case WAITING_FOR_USER -> "clarify";
                case WAITING_FOR_APPROVAL -> "waiting_for_approval";
                case ESCALATED -> "escalated";
                case GAP -> "gap";
                case CANCELLED -> "cancelled";
                default -> "complete";
            };
            normalized.put("kind", kind);
        }
        return terminalNormalizer.normalize(normalized);
    }

    /**
     * Factory Method: creates an immutable harness run snapshot with common
     * identity and timestamp fields filled from the harness and context.
     */
    private AgentHarnessRunState snapshot(
            ExecutionContext context,
            AgentHarnessDefinition harness,
            String runId,
            AgentHarnessStatus status,
            int turn,
            Instant createdAt,
            Map<String, Object> originalInput,
            Map<String, Object> latestInput,
            List<AgentHarnessObservation> observations,
            Map<String, Object> output,
            String errorCode,
            String errorMessage
    ) {
        return new AgentHarnessRunState(
                context == null ? null : context.tenantId(),
                runId,
                harness.id(),
                harness.version(),
                status,
                turn,
                originalInput,
                latestInput,
                observations,
                output,
                errorCode,
                errorMessage,
                createdAt,
                Instant.now(),
                envelopeFor(status, output, errorCode, errorMessage));
    }

    /**
     * Store operation: records the latest in-memory harness run snapshot and
     * returns it to keep the loop code expression-oriented.
     */
    private AgentHarnessRunState save(AgentHarnessRunState state) {
        runs.put(state.runId(), state);
        return state;
    }

    /**
     * Store operation: loads a known harness run or reports a structured caller
     * error when resume/cancel targets an unknown run id.
     */
    private AgentHarnessRunState requireRun(String runId) {
        AgentHarnessRunState state = runs.get(runId);
        if (state == null) {
            throw new IllegalArgumentException("Harness run not found: " + runId);
        }
        return state;
    }

    /**
     * Builder: returns a copy of a previous snapshot with a new status and
     * terminal detail while preserving run identity and history.
     */
    private AgentHarnessRunState withStatus(
            AgentHarnessRunState current,
            AgentHarnessStatus status,
            Map<String, Object> output,
            String errorCode,
            String errorMessage
    ) {
        return new AgentHarnessRunState(current.tenantId(), current.runId(), current.harnessId(),
                current.harnessVersion(), status, current.turn(), current.originalInput(), current.latestInput(),
                current.observations(), output, errorCode, errorMessage, current.createdAt(), Instant.now(),
                envelopeFor(status, output, errorCode, errorMessage));
    }

    /**
     * Predicate: identifies harness states that should not be mutated by cancel
     * or resumed as if they were waiting for user input.
     */
    private boolean isTerminal(AgentHarnessStatus status) {
        return status == AgentHarnessStatus.COMPLETED
                || status == AgentHarnessStatus.ESCALATED
                || status == AgentHarnessStatus.GAP
                || status == AgentHarnessStatus.FAILED
                || status == AgentHarnessStatus.CANCELLED;
    }

    /**
     * Predicate: checks the optional embedded wall-clock guardrail without
     * adding scheduling or timers to the substrate.
     */
    private boolean deadlineExceeded(Instant createdAt, AgentHarnessLoopPolicy policy) {
        return policy.maxDurationMillis() > 0
                && Duration.between(createdAt, Instant.now()).toMillis() > policy.maxDurationMillis();
    }

    /**
     * Reads terminal phase metadata from the wrapped agent definition. Accepts
     * either a single `terminalPhaseId` or a collection `terminalPhaseIds`.
     */
    private List<String> terminalPhaseIds(Map<String, Object> metadata) {
        List<String> ids = new ArrayList<>();
        Object single = metadata.get("terminalPhaseId");
        if (single instanceof String value && !value.isBlank()) {
            ids.add(value);
        }
        Object many = metadata.get("terminalPhaseIds");
        if (many instanceof Collection<?> collection) {
            for (Object value : collection) {
                if (value != null && !String.valueOf(value).isBlank()) {
                    ids.add(String.valueOf(value));
                }
            }
        }
        return List.copyOf(ids);
    }

    /**
     * Reads deployment metadata that constrains acceptable terminal output
     * kinds while keeping the harness generic.
     */
    private List<String> terminalKinds(Map<String, Object> metadata) {
        Object configured = metadata.get("terminalKinds");
        if (configured instanceof Collection<?> collection) {
            List<String> kinds = new ArrayList<>();
            for (Object value : collection) {
                if (value != null && !String.valueOf(value).isBlank()) {
                    kinds.add(String.valueOf(value));
                }
            }
            return List.copyOf(kinds);
        }
        if (configured instanceof String value && !value.isBlank()) {
            return List.of(value.split(",")).stream()
                    .map(String::trim)
                    .filter(kind -> !kind.isBlank())
                    .toList();
        }
        return List.of();
    }

    /**
     * Adapter: converts arbitrary map keys from model/tool output into stable
     * string keys before using them as the next harness input.
     */
    private Map<String, Object> stringifyKeys(Map<?, ?> value) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        value.forEach((key, nextValue) -> normalized.put(String.valueOf(key), normalizeValue(nextValue)));
        return Map.copyOf(normalized);
    }

    /**
     * Adapter: recursively normalizes maps nested inside the next-input object
     * without changing lists or scalar values.
     */
    private Object normalizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return stringifyKeys(map);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::normalizeValue).toList();
        }
        return value == null ? "" : value;
    }

    /**
     * Selector: extracts a human-readable observation message without requiring
     * prompts or raw model content to be logged.
     */
    private String messageFrom(Map<String, Object> output, String fallback) {
        Object assistantMessage = output.get("assistantMessage");
        if (assistantMessage != null && !String.valueOf(assistantMessage).isBlank()) {
            return String.valueOf(assistantMessage);
        }
        Object message = output.get("message");
        if (message != null && !String.valueOf(message).isBlank()) {
            return String.valueOf(message);
        }
        return fallback;
    }

    /**
     * Adapter: extracts the first JSON object from a content string, matching
     * the existing agent-run DCP adapter behavior.
     */
    private String extractJson(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        return start >= 0 && end > start ? content.substring(start, end + 1) : content;
    }

    /**
     * Adapter: stringifies nullable scalar output values for case-insensitive
     * decision checks.
     */
    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * Strategy result: carries the interpreted harness decision and optional
     * next input/error detail between the selector and run loop.
     */
    private record Decision(
            AgentHarnessStatus status,
            String kind,
            Map<String, Object> nextInput,
            String errorCode,
            String message
    ) {
        /**
         * Constructs Decision with an empty immutable next input when none is
         * needed by the interpreted harness state.
         */
        private Decision {
            nextInput = nextInput == null ? Map.of() : Map.copyOf(nextInput);
        }

        /**
         * Factory method: creates a failed harness decision with structured
         * error code and message.
         */
        private static Decision failure(String kind, String errorCode, String message) {
            return new Decision(AgentHarnessStatus.FAILED, kind, Map.of(), errorCode, message);
        }
    }
}
