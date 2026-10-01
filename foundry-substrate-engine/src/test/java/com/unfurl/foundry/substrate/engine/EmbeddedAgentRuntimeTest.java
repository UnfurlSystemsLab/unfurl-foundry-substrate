package com.unfurl.foundry.substrate.engine;

import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.agent.AgentPhase;
import com.unfurl.foundry.substrate.agent.BudgetPolicy;
import com.unfurl.foundry.substrate.agent.CorrectionPolicy;
import com.unfurl.foundry.substrate.agent.CorrectionExhaustionAction;
import com.unfurl.foundry.substrate.guardrail.CostGuardrailContext;
import com.unfurl.foundry.substrate.failure.FailureCategory;
import com.unfurl.foundry.substrate.failure.StructuredFailure;
import com.unfurl.foundry.substrate.model.Message;
import com.unfurl.foundry.substrate.model.ModelResponse;
import com.unfurl.foundry.substrate.model.ModelToolCall;
import com.unfurl.foundry.substrate.model.ModelTurnOutcome;
import com.unfurl.foundry.substrate.model.ModelUsage;
import com.unfurl.foundry.substrate.model.MessageRole;
import com.unfurl.foundry.substrate.ports.ModelProvider;
import com.unfurl.foundry.substrate.ports.ValidationIssue;
import com.unfurl.foundry.substrate.ports.ValidationResult;
import com.unfurl.foundry.substrate.ports.ToolCallResult;
import com.unfurl.foundry.substrate.ports.ToolCallDecision;
import com.unfurl.foundry.substrate.ports.ToolCallInterceptor;
import com.unfurl.foundry.substrate.ports.ToolCallInterceptorChain;
import com.unfurl.foundry.substrate.runstate.AgentPhaseStatus;
import com.unfurl.foundry.substrate.runstate.AgentRunState;
import com.unfurl.foundry.substrate.runstate.AgentRunStatus;
import com.unfurl.foundry.substrate.testing.RecordingToolExecutor;
import com.unfurl.foundry.substrate.testing.ScriptedModelProvider;
import com.unfurl.foundry.substrate.testing.StaticProviderRegistry;
import com.unfurl.foundry.substrate.tools.DefaultToolRegistry;
import com.unfurl.substrate.domain.ConditionDefinition;
import com.unfurl.substrate.domain.EdgeDefinition;
import com.unfurl.substrate.policy.ExecutionContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddedAgentRuntimeTest {

    /** Verifies the injected Strategy surrounds both physical provider and admitted tool dispatch. */
    @Test
    void routesProviderAndToolThroughExternalBoundary() {
        StaticProviderRegistry providers = new StaticProviderRegistry().registerModel("model",
                new ScriptedModelProvider(List.of(
                        response("call tool", List.of(new ModelToolCall("call-1", "lookup", Map.of()))),
                        response("done", List.of()))));
        DefaultToolRegistry tools = new DefaultToolRegistry();
        tools.register("lookup", new RecordingToolExecutor(Map.of("found", true)));
        List<ExternalCallBoundary.Kind> dispatched = new java.util.ArrayList<>();
        ExternalCallBoundary boundary = new ExternalCallBoundary() {
            /** Recording Strategy: observes the physical call and invokes its supplier exactly once. */
            @Override public <T> T invoke(Call call, java.util.function.Supplier<T> invocation) {
                dispatched.add(call.kind());
                if (call.kind() == Kind.TOOL) {
                    var request = (com.unfurl.foundry.substrate.ports.ToolCallRequest) call.request();
                    var scope = com.unfurl.foundry.substrate.ports.ToolCallScope.from(request.metadata());
                    assertThat(scope.agentRunId()).isEqualTo(call.runId());
                    assertThat(scope.toolCallId()).isEqualTo(call.callId()).isNotEqualTo(request.callId());
                    assertThat(scope.phaseId()).isEqualTo("first");
                    assertThat(scope.tenantId()).isNull();
                }
                return invocation.get();
            }
        };
        EmbeddedAgentRuntime runtime = new EmbeddedAgentRuntime(providers, tools, null,
                new com.unfurl.foundry.substrate.guardrail.BudgetPolicyCostGuardrail(),
                new AllowAllPermissionBridge(), null, new com.unfurl.foundry.substrate.prompt.PromptAssembler(),
                new com.unfurl.foundry.substrate.resolver.DataReferenceResolver(),
                new com.unfurl.foundry.substrate.agent.AgentDefinitionValidator(), null, Map.of(),
                ToolCallInterceptorChain.empty(), null, null, boundary);
        AgentDefinition agent = new AgentDefinition("agent", "1", Map.of(),
                List.of(phase("first", Map.of("prompt", "lookup"), List.of("lookup"), 1)),
                List.of(), Map.of(), "model", List.of("lookup"));

        AgentRunState run = runtime.start(agent, Map.of(), ExecutionContext.empty());

        assertThat(run.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(dispatched).containsExactly(ExternalCallBoundary.Kind.PROVIDER,
                ExternalCallBoundary.Kind.TOOL, ExternalCallBoundary.Kind.PROVIDER);
    }

    /** Structural rejection precedes semantic validation and precise feedback drives one repair. */
    @Test
    void validatesInOrderAndCorrectsWithinPolicy() {
        ScriptedModelProvider model = new ScriptedModelProvider(List.of(
                response("not-json", List.of()),
                response("{\"amount\":2}", List.of())));
        StaticProviderRegistry providers = new StaticProviderRegistry().registerModel("model", model);
        AtomicInteger semanticCalls = new AtomicInteger();
        EmbeddedAgentRuntime runtime = runtimeWithValidation(
                providers,
                (schemaRef, value, context) -> value instanceof Map<?, ?> map && map.get("amount") instanceof Number
                        ? ValidationResult.success()
                        : ValidationResult.invalid(List.of(new ValidationIssue(
                        "AMOUNT_REQUIRED", "$.amount", "amount must be numeric", Map.of()))),
                (validatorRef, context) -> Optional.of((value, source, executionContext) -> {
                    semanticCalls.incrementAndGet();
                    return ValidationResult.success();
                }));
        AgentPhase phase = validationPhase(new CorrectionPolicy(
                1, 5_000, CorrectionExhaustionAction.FAIL, Map.of()));
        AgentDefinition agent = new AgentDefinition("agent", "1", Map.of(), List.of(phase),
                List.of(), Map.of(), "model", List.of());

        AgentRunState run = runtime.start(agent, Map.of(), ExecutionContext.empty());

        assertThat(run.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(run.phases().get("validated").output()).containsEntry("amount", 2);
        assertThat(semanticCalls).hasValue(1);
        assertThat(run.phases().get("validated").messages()).anySatisfy(message ->
                assertThat(message.content()).contains("AMOUNT_REQUIRED", "$.amount"));
    }

    /** Correction exhaustion is finite and returns the stable structured validation failure code. */
    @Test
    void failsAfterBoundedSemanticCorrection() {
        ScriptedModelProvider model = new ScriptedModelProvider(List.of(
                response("{\"amount\":-1}", List.of()),
                response("{\"amount\":-2}", List.of())));
        StaticProviderRegistry providers = new StaticProviderRegistry().registerModel("model", model);
        AtomicInteger semanticCalls = new AtomicInteger();
        EmbeddedAgentRuntime runtime = runtimeWithValidation(
                providers,
                (schemaRef, value, context) -> ValidationResult.success(),
                (validatorRef, context) -> Optional.of((value, source, executionContext) -> {
                    semanticCalls.incrementAndGet();
                    return ValidationResult.invalid(List.of(new ValidationIssue(
                            "AMOUNT_POSITIVE", "$.amount", "amount must be positive", Map.of())));
                }));
        AgentPhase phase = validationPhase(new CorrectionPolicy(
                1, 5_000, CorrectionExhaustionAction.FAIL, Map.of()));
        AgentDefinition agent = new AgentDefinition("agent", "1", Map.of(), List.of(phase),
                List.of(), Map.of(), "model", List.of());

        AgentRunState run = runtime.start(agent, Map.of(), ExecutionContext.empty());

        assertThat(run.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(run.errorCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(semanticCalls).hasValue(2);
    }

    /** A policy denial stops the tool call before registry execution. */
    @Test
    void interceptorDenialNeverReachesToolExecutor() {
        StaticProviderRegistry providers = new StaticProviderRegistry()
                .registerModel("model", new ScriptedModelProvider(List.of(
                        response("tool", List.of(new ModelToolCall("call-1", "lookup", Map.of()))))));
        RecordingToolExecutor executor = new RecordingToolExecutor(Map.of("ok", true));
        DefaultToolRegistry tools = new DefaultToolRegistry();
        tools.register("lookup", executor);
        ToolCallInterceptor deny = new ToolCallInterceptor() {
            /** Denies the call with a deterministic prerequisite failure. */
            @Override
            public ToolCallDecision before(
                    com.unfurl.foundry.substrate.ports.ToolCallRequest request, ExecutionContext context) {
                return ToolCallDecision.deny(request.arguments(), StructuredFailure.terminal(
                        "PREREQUISITE_MISSING", FailureCategory.AUTHORIZATION, "token required"), Map.of());
            }

            /** Leaves results unchanged; execution is unreachable for this policy. */
            @Override
            public ToolCallResult after(
                    com.unfurl.foundry.substrate.ports.ToolCallRequest request,
                    ToolCallResult result,
                    ExecutionContext context) {
                return result;
            }
        };
        EmbeddedAgentRuntime runtime = runtimeWithChain(
                providers, tools, new ToolCallInterceptorChain(List.of(deny)));
        AgentDefinition agent = new AgentDefinition("agent", "1", Map.of(),
                List.of(phase("first", Map.of("prompt", "lookup"), List.of("lookup"), 1)),
                List.of(), Map.of(), "model", List.of("lookup"));

        AgentRunState run = runtime.start(agent, Map.of(), ExecutionContext.empty());

        assertThat(run.errorCode()).isEqualTo("PREREQUISITE_MISSING");
        assertThat(executor.calls()).isEmpty();
    }

    @Test
    void treatsStringFalseConditionAsFalseAndSkipsDownstreamPhase() {
        StaticProviderRegistry providers = new StaticProviderRegistry()
                .registerModel("model", new ScriptedModelProvider(List.of(
                        response("false", List.of())
                )));
        EmbeddedAgentRuntime runtime = new EmbeddedAgentRuntime(providers, new DefaultToolRegistry());
        AgentDefinition agent = new AgentDefinition(
                "agent",
                "1",
                Map.of(),
                List.of(phase("first", Map.of("prompt", "decide"), List.of(), 0),
                        phase("second", Map.of("prompt", "run"), List.of(), 0)),
                List.of(new EdgeDefinition("first", "second", new ConditionDefinition("$.phases.first.output.content"))),
                Map.of(),
                "model",
                List.of()
        );

        AgentRunState run = runtime.start(agent, Map.of(), ExecutionContext.empty());

        assertThat(run.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(run.phases().get("first").status()).isEqualTo(AgentPhaseStatus.COMPLETED);
        assertThat(run.phases().get("second").status()).isEqualTo(AgentPhaseStatus.SKIPPED);
    }

    @Test
    void rejectsModelToolCallsOutsideAgentOrPhaseAllowLists() {
        StaticProviderRegistry providers = new StaticProviderRegistry()
                .registerModel("model", new ScriptedModelProvider(List.of(
                        response("tool please", List.of(new ModelToolCall("call-1", "external", Map.of())))
                )));
        RecordingToolExecutor executor = new RecordingToolExecutor(Map.of("ok", true));
        DefaultToolRegistry tools = new DefaultToolRegistry();
        tools.register("external", executor);
        EmbeddedAgentRuntime runtime = new EmbeddedAgentRuntime(providers, tools);
        AgentDefinition agent = new AgentDefinition(
                "agent",
                "1",
                Map.of(),
                List.of(phase("first", Map.of("prompt", "use tool"), List.of("approved"), 1)),
                List.of(),
                Map.of(),
                "model",
                List.of("approved")
        );

        AgentRunState run = runtime.start(agent, Map.of(), ExecutionContext.empty());

        assertThat(run.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(run.errorCode()).isEqualTo("TOOL_NOT_ALLOWED");
        assertThat(executor.calls()).isEmpty();
    }

    /**
     * Verifies structured tool failures retain their stable code, partial output, and call evidence.
     */
    @Test
    void preservesStructuredToolFailureAndPartialOutput() {
        StaticProviderRegistry providers = new StaticProviderRegistry()
                .registerModel("model", new ScriptedModelProvider(List.of(
                        response("call tool", List.of(new ModelToolCall("call-1", "lookup", Map.of())))
                )));
        DefaultToolRegistry tools = new DefaultToolRegistry();
        tools.register("lookup", (request, context) -> ToolCallResult.failure(new StructuredFailure(
                "LOOKUP_RATE_LIMITED",
                FailureCategory.RATE_LIMIT,
                true,
                500L,
                "lookup capacity exhausted",
                Map.of("accepted", 2),
                Map.of("limit", 2),
                Map.of("tool", "lookup"))));
        EmbeddedAgentRuntime runtime = new EmbeddedAgentRuntime(providers, tools);
        AgentDefinition agent = new AgentDefinition(
                "agent", "1", Map.of(),
                List.of(phase("first", Map.of("prompt", "lookup"), List.of("lookup"), 1)),
                List.of(), Map.of(), "model", List.of("lookup"));

        AgentRunState run = runtime.start(agent, Map.of(), ExecutionContext.empty());

        assertThat(run.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(run.errorCode()).isEqualTo("LOOKUP_RATE_LIMITED");
        assertThat(run.phases().get("first").output()).containsEntry("accepted", 2);
        assertThat(run.phases().get("first").toolCalls()).singleElement()
                .satisfies(call -> assertThat(call.result()).containsEntry("accepted", 2));
    }

    @Test
    void failsWhenModelStillRequestsToolsAfterIterationBudget() {
        StaticProviderRegistry providers = new StaticProviderRegistry()
                .registerModel("model", new ScriptedModelProvider(List.of(
                        response("first tool", List.of(new ModelToolCall("call-1", "lookup", Map.of()))),
                        response("second tool", List.of(new ModelToolCall("call-2", "lookup", Map.of())))
                )));
        RecordingToolExecutor executor = new RecordingToolExecutor(Map.of("answer", 42));
        DefaultToolRegistry tools = new DefaultToolRegistry();
        tools.register("lookup", executor);
        EmbeddedAgentRuntime runtime = new EmbeddedAgentRuntime(providers, tools);
        AgentDefinition agent = new AgentDefinition(
                "agent",
                "1",
                Map.of(),
                List.of(phase("first", Map.of("prompt", "use tool"), List.of("lookup"), 1)),
                List.of(),
                Map.of(),
                "model",
                List.of("lookup")
        );

        AgentRunState run = runtime.start(agent, Map.of(), ExecutionContext.empty());

        assertThat(run.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(run.errorCode()).isEqualTo("MAX_TOOL_ITERATIONS_EXCEEDED");
        assertThat(executor.calls()).hasSize(1);
    }

    /**
     * Verifies provider exceptions become structured, sanitized MODEL_FAILED run state.
     */
    @Test
    void convertsProviderExceptionIntoSanitizedModelFailure() {
        StaticProviderRegistry providers = new StaticProviderRegistry()
                .registerModel("model", new ThrowingModelProvider());
        EmbeddedAgentRuntime runtime = new EmbeddedAgentRuntime(providers, new DefaultToolRegistry());
        AgentDefinition agent = new AgentDefinition(
                "agent",
                "1",
                Map.of(),
                List.of(phase("first", Map.of("prompt", "call model"), List.of(), 0)),
                List.of(),
                Map.of(),
                "model",
                List.of()
        );

        AgentRunState run = runtime.start(agent, Map.of(), ExecutionContext.empty());

        assertThat(run.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(run.errorCode()).isEqualTo("MODEL_FAILED");
        assertThat(run.errorMessage())
                .contains("Model provider failed for ref model")
                .contains("Failed to generate content")
                .contains("models/gemini-3.1-flash-lite")
                .contains("<redacted>")
                .doesNotContain("AIza");
        assertThat(run.phases().get("first").errorCode()).isEqualTo("MODEL_FAILED");
    }

    /**
     * Verifies provider-neutral non-success outcomes become distinct structured runtime failures.
     */
    @Test
    void mapsNonSuccessModelOutcomesToStableFailureCodes() {
        assertModelOutcomeFailure(ModelTurnOutcome.MAX_OUTPUT_REACHED, "MODEL_MAX_OUTPUT_REACHED");
        assertModelOutcomeFailure(ModelTurnOutcome.CONTENT_FILTERED, "MODEL_CONTENT_FILTERED");
        assertModelOutcomeFailure(ModelTurnOutcome.PROVIDER_ERROR, "MODEL_PROVIDER_ERROR");
    }

    @Test
    void appendsToolResultsAsJsonToolMessages() {
        CapturingToolResultModelProvider model = new CapturingToolResultModelProvider();
        StaticProviderRegistry providers = new StaticProviderRegistry().registerModel("model", model);
        RecordingToolExecutor executor = new RecordingToolExecutor(Map.of(
                "answer", 42,
                "nested", Map.of("ok", true)));
        DefaultToolRegistry tools = new DefaultToolRegistry();
        tools.register("lookup", executor);
        EmbeddedAgentRuntime runtime = new EmbeddedAgentRuntime(providers, tools);
        AgentDefinition agent = new AgentDefinition(
                "agent",
                "1",
                Map.of(),
                List.of(phase("first", Map.of("prompt", "use tool"), List.of("lookup"), 1)),
                List.of(),
                Map.of(),
                "model",
                List.of("lookup")
        );

        AgentRunState run = runtime.start(agent, Map.of(), ExecutionContext.empty());

        assertThat(run.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(model.toolMessageContent())
                .contains("\"answer\":42")
                .contains("\"nested\":{\"ok\":true")
                .doesNotContain("answer=42");
        assertThat(run.phases().get("first").messages())
                .filteredOn(message -> message.role() == MessageRole.TOOL)
                .extracting(Message::content)
                .containsExactly(model.toolMessageContent());
    }

    @Test
    void completesMappedToolPhaseWithoutSecondModelTurn() {
        StaticProviderRegistry providers = new StaticProviderRegistry()
                .registerModel("model", new ScriptedModelProvider(List.of(
                        response("{\"toolCalls\":[{\"id\":\"step-02-catalog-admit\",\"toolName\":\"catalog.admit\",\"arguments\":{}}]}",
                                List.of(new ModelToolCall("step-02-catalog-admit", "catalog.admit", Map.of())))
                )));
        RecordingToolExecutor executor = new RecordingToolExecutor(Map.of(
                "status", "PASS",
                "artifact", Map.of("sha256", "sha256:abc")));
        DefaultToolRegistry tools = new DefaultToolRegistry();
        tools.register("catalog.admit", executor);
        EmbeddedAgentRuntime runtime = new EmbeddedAgentRuntime(providers, tools);
        AgentPhase phase = new AgentPhase(
                "execute",
                null,
                null,
                List.of("catalog.admit"),
                null,
                Map.of("prompt", "execute"),
                Map.of(
                        "kind", "execution",
                        "phase", "catalog-creation",
                        "step", 2,
                        "toolResult", "$.tools.step-02-catalog-admit.output",
                        "toolCalls", List.of(Map.of(
                                "id", "step-02-catalog-admit",
                                "toolName", "catalog.admit",
                                "status", "$.tools.step-02-catalog-admit.output.status")),
                        "artifacts", List.of(Map.of(
                                "sha256", "$.tools.step-02-catalog-admit.output.artifact.sha256"))),
                List.of(),
                1);
        AgentDefinition agent = new AgentDefinition(
                "agent",
                "1",
                Map.of(),
                List.of(phase),
                List.of(),
                Map.of(),
                "model",
                List.of("catalog.admit"));

        AgentRunState run = runtime.start(agent, Map.of(), ExecutionContext.empty());

        assertThat(run.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(executor.calls()).hasSize(1);
        assertThat(run.phases().get("execute").output())
                .containsEntry("kind", "execution")
                .containsEntry("phase", "catalog-creation")
                .containsEntry("step", 2);
        assertThat(run.phases().get("execute").toolCalls()).hasSize(1);
    }

    @Test
    void failsBeforeNextPhaseWhenResolvedAgentBudgetIsExhausted() {
        StaticProviderRegistry providers = new StaticProviderRegistry()
                .registerModel("model", new ScriptedModelProvider(List.of(
                        response("spent", List.of(), new BigDecimal("5.00"))
                )));
        EmbeddedAgentRuntime runtime = new EmbeddedAgentRuntime(providers, new DefaultToolRegistry());
        AgentDefinition agent = twoPhaseAgent(new BudgetPolicy(new BigDecimal("5.00"), null,
                null, null, null, Map.of()));

        AgentRunState run = runtime.start(agent, Map.of(), ExecutionContext.empty());

        assertThat(run.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(run.errorCode()).isEqualTo("GUARDRAIL_TRIPPED");
        assertThat(run.phases().get("first").status()).isEqualTo(AgentPhaseStatus.COMPLETED);
        assertThat(run.phases().get("second").status()).isEqualTo(AgentPhaseStatus.FAILED);
    }

    @Test
    void failsBeforeNextPhaseWhenOuterRunBudgetIsLowerThanAgentBudget() {
        StaticProviderRegistry providers = new StaticProviderRegistry()
                .registerModel("model", new ScriptedModelProvider(List.of(
                        response("spent", List.of(), new BigDecimal("3.00"))
                )));
        EmbeddedAgentRuntime runtime = new EmbeddedAgentRuntime(providers, new DefaultToolRegistry());
        AgentDefinition agent = twoPhaseAgent(new BudgetPolicy(new BigDecimal("10.00"), null,
                null, null, null, Map.of()));
        ExecutionContext context = withOuterBudget(new BigDecimal("3.00"));

        AgentRunState run = runtime.start(agent, Map.of(), context);

        assertThat(run.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(run.errorCode()).isEqualTo("GUARDRAIL_TRIPPED");
        assertThat(run.phases().get("first").status()).isEqualTo(AgentPhaseStatus.COMPLETED);
        assertThat(run.phases().get("second").status()).isEqualTo(AgentPhaseStatus.FAILED);
    }

    @Test
    void mapsJsonPhaseOutputIntoDownstreamInput() {
        StaticProviderRegistry providers = new StaticProviderRegistry()
                .registerModel("model", new ScriptedModelProvider(List.of(
                        response("{\"category\":\"billing\",\"readyToPropose\":true}", List.of()),
                        response("done", List.of())
                )));
        EmbeddedAgentRuntime runtime = new EmbeddedAgentRuntime(providers, new DefaultToolRegistry());
        AgentPhase classify = new AgentPhase("classify", null, null, List.of(), null,
                Map.of("prompt", "classify"), Map.of(
                "category", "$.output.category",
                "readyToPropose", "$.output.readyToPropose"), List.of(), 0);
        AgentPhase resolve = new AgentPhase("resolve", null, null, List.of(), null,
                Map.of("prompt", "$.phases.classify.output.category"), Map.of(), List.of("classify"), 0);
        AgentDefinition agent = new AgentDefinition("agent", "1", Map.of(), List.of(classify, resolve),
                List.of(), Map.of(), "model", List.of());

        AgentRunState run = runtime.start(agent, Map.of(), ExecutionContext.empty());

        assertThat(run.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(run.phases().get("classify").output())
                .containsEntry("content", "{\"category\":\"billing\",\"readyToPropose\":true}")
                .containsEntry("category", "billing")
                .containsEntry("readyToPropose", true);
        assertThat(run.phases().get("resolve").input()).containsEntry("prompt", "billing");
    }

    @Test
    void routesConditionalEdgesUsingMappedBooleanOutput() {
        StaticProviderRegistry providers = new StaticProviderRegistry()
                .registerModel("model", new ScriptedModelProvider(List.of(
                        response("{\"readyToPropose\":false}", List.of())
                )));
        EmbeddedAgentRuntime runtime = new EmbeddedAgentRuntime(providers, new DefaultToolRegistry());
        AgentPhase classify = new AgentPhase("classify", null, null, List.of(), null,
                Map.of("prompt", "classify"), Map.of("readyToPropose", "$.output.readyToPropose"), List.of(), 0);
        AgentPhase propose = phase("propose", Map.of("prompt", "propose"), List.of(), 0);
        AgentDefinition agent = new AgentDefinition("agent", "1", Map.of(), List.of(classify, propose),
                List.of(new EdgeDefinition("classify", "propose",
                        new ConditionDefinition("$.phases.classify.output.readyToPropose"))),
                Map.of(), "model", List.of());

        AgentRunState run = runtime.start(agent, Map.of(), ExecutionContext.empty());

        assertThat(run.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(run.phases().get("classify").status()).isEqualTo(AgentPhaseStatus.COMPLETED);
        assertThat(run.phases().get("propose").status()).isEqualTo(AgentPhaseStatus.SKIPPED);
    }

    @Test
    void routesConditionalEdgesUsingStringEqualityOutput() {
        StaticProviderRegistry providers = new StaticProviderRegistry()
                .registerModel("model", new ScriptedModelProvider(List.of(
                        response("{\"kind\":\"continue\"}", List.of()),
                        response("{\"kind\":\"proposal\"}", List.of())
                )));
        EmbeddedAgentRuntime runtime = new EmbeddedAgentRuntime(providers, new DefaultToolRegistry());
        AgentPhase classify = phase("classify", Map.of("prompt", "classify"), List.of(), 0);
        AgentPhase propose = phase("propose", Map.of("prompt", "propose"), List.of(), 0);
        AgentDefinition agent = new AgentDefinition("agent", "1", Map.of(), List.of(classify, propose),
                List.of(new EdgeDefinition("classify", "propose",
                        new ConditionDefinition("$.phases.classify.output.kind == 'continue'"))),
                Map.of(), "model", List.of());

        AgentRunState run = runtime.start(agent, Map.of(), ExecutionContext.empty());

        assertThat(run.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(run.phases().get("classify").status()).isEqualTo(AgentPhaseStatus.COMPLETED);
        assertThat(run.phases().get("propose").status()).isEqualTo(AgentPhaseStatus.COMPLETED);
        assertThat(run.phases().get("propose").output()).containsEntry("kind", "proposal");
    }

    @Test
    void failsPhaseWhenRequiredOutputMappingCannotResolve() {
        StaticProviderRegistry providers = new StaticProviderRegistry()
                .registerModel("model", new ScriptedModelProvider(List.of(
                        response("{}", List.of())
                )));
        EmbeddedAgentRuntime runtime = new EmbeddedAgentRuntime(providers, new DefaultToolRegistry());
        AgentPhase phase = new AgentPhase("classify", null, null, List.of(), null,
                Map.of("prompt", "classify"), Map.of("category", "$.output.category"), List.of(), 0);
        AgentDefinition agent = new AgentDefinition("agent", "1", Map.of(), List.of(phase),
                List.of(), Map.of(), "model", List.of());

        AgentRunState run = runtime.start(agent, Map.of(), ExecutionContext.empty());

        assertThat(run.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(run.errorCode()).isEqualTo("OUTPUT_MAPPING_UNRESOLVED");
        assertThat(run.phases().get("classify").errorCode()).isEqualTo("OUTPUT_MAPPING_UNRESOLVED");
    }

    @Test
    void recordsTypedProviderNameAndEstimatedCost() {
        StaticProviderRegistry providers = new StaticProviderRegistry()
                .registerModel("model", new ScriptedModelProvider(List.of(
                        new ModelResponse(Message.assistant("spent"), List.of(), "stop", new ModelUsage(2, 3),
                                Map.of(), "test-provider", new BigDecimal("0.42"))
                )));
        EmbeddedAgentRuntime runtime = new EmbeddedAgentRuntime(providers, new DefaultToolRegistry());
        AgentDefinition agent = new AgentDefinition(
                "agent",
                "1",
                Map.of(),
                List.of(phase("first", Map.of("prompt", "first"), List.of(), 0)),
                List.of(),
                Map.of(),
                "model",
                List.of()
        );

        AgentRunState run = runtime.start(agent, Map.of(), ExecutionContext.empty());

        assertThat(run.cost().tokensByProvider()).containsEntry("test-provider", 5L);
        assertThat(run.cost().estimatedCostUsd()).isEqualByComparingTo("0.42");
    }

    private AgentPhase phase(String id, Map<String, Object> input, List<String> allowedTools, int maxToolIterations) {
        return new AgentPhase(id, null, null, allowedTools, null, input, Map.of(), List.of(), maxToolIterations);
    }

    /** Fixture Factory: constructs the full runtime with a selected tool interceptor chain. */
    private EmbeddedAgentRuntime runtimeWithChain(
            StaticProviderRegistry providers,
            DefaultToolRegistry tools,
            ToolCallInterceptorChain chain) {
        return new EmbeddedAgentRuntime(
                providers, tools, null, new com.unfurl.foundry.substrate.guardrail.BudgetPolicyCostGuardrail(),
                new AllowAllPermissionBridge(), null, new com.unfurl.foundry.substrate.prompt.PromptAssembler(),
                new com.unfurl.foundry.substrate.resolver.DataReferenceResolver(),
                new com.unfurl.foundry.substrate.agent.AgentDefinitionValidator(), null, Map.of(), chain);
    }

    /** Fixture Factory: creates a runtime with explicit structural and semantic validation ports. */
    private EmbeddedAgentRuntime runtimeWithValidation(
            StaticProviderRegistry providers,
            com.unfurl.foundry.substrate.ports.OutputSchemaValidator schemaValidator,
            com.unfurl.foundry.substrate.ports.SemanticValidatorRegistry semanticRegistry) {
        return new EmbeddedAgentRuntime(
                providers, new DefaultToolRegistry(), null,
                new com.unfurl.foundry.substrate.guardrail.BudgetPolicyCostGuardrail(),
                new AllowAllPermissionBridge(), null,
                new com.unfurl.foundry.substrate.prompt.PromptAssembler(),
                new com.unfurl.foundry.substrate.resolver.DataReferenceResolver(),
                new com.unfurl.foundry.substrate.agent.AgentDefinitionValidator(), null, Map.of(),
                ToolCallInterceptorChain.empty(), schemaValidator, semanticRegistry);
    }

    /** Verifies progress is saved before each repair and a failed observer prevents the next provider call. */
    @Test void checkpointsCorrectionBeforeRepairDispatch() {
        var calls = new AtomicInteger();
        var providers = new StaticProviderRegistry().registerModel("model", (request, context) -> {
            calls.incrementAndGet();
            return response("not-json", List.of());
        });
        var progress = new java.util.ArrayList<com.unfurl.foundry.substrate.ports.CorrectionProgress>();
        var runtime = new EmbeddedAgentRuntime(providers, new DefaultToolRegistry(), null,
                new com.unfurl.foundry.substrate.guardrail.BudgetPolicyCostGuardrail(), new AllowAllPermissionBridge(), null,
                new com.unfurl.foundry.substrate.prompt.PromptAssembler(), new com.unfurl.foundry.substrate.resolver.DataReferenceResolver(),
                new com.unfurl.foundry.substrate.agent.AgentDefinitionValidator(), null, Map.of(), ToolCallInterceptorChain.empty(),
                (schema, value, context) -> ValidationResult.invalid(List.of(new ValidationIssue("INVALID", "$", "invalid", Map.of()))),
                (ref, context) -> Optional.empty(), ExternalCallBoundary.direct(),
                com.unfurl.foundry.substrate.ports.ModelRequestProjector.identity(), (value, context) -> {
                    progress.add(value);
                    if (value.status() == com.unfurl.foundry.substrate.ports.CorrectionProgress.Status.REPAIR_REQUESTED) {
                        throw new IllegalStateException("progress store unavailable");
                    }
                });
        var phase = validationPhase(new CorrectionPolicy(1, 60000, CorrectionExhaustionAction.FAIL, Map.of()));
        var agent = new AgentDefinition("agent", "1", Map.of(), List.of(phase), List.of(), Map.of(), "model", List.of());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> runtime.start(agent, Map.of(), ExecutionContext.empty()))
                .hasMessageContaining("progress store unavailable");
        assertThat(calls).hasValue(1);
        assertThat(progress.stream().map(com.unfurl.foundry.substrate.ports.CorrectionProgress::status).toList())
                .containsExactly(com.unfurl.foundry.substrate.ports.CorrectionProgress.Status.STARTED,
                        com.unfurl.foundry.substrate.ports.CorrectionProgress.Status.REPAIR_REQUESTED);
        assertThat(progress.getLast().startedAt()).isEqualTo(progress.getFirst().startedAt());
        assertThat(progress.getLast().attempt()).isEqualTo(1);
    }

    /** Fixture Factory: creates one phase with schema, semantic, mapping, and correction declarations. */
    private AgentPhase validationPhase(CorrectionPolicy correctionPolicy) {
        return new AgentPhase(
                "validated", null, null, List.of(), null, Map.of("prompt", "produce amount"),
                Map.of("amount", "$.output.amount"), List.of(), 0, List.of(),
                "amount-schema", "positive-amount", correctionPolicy);
    }

    private ModelResponse response(String content, List<ModelToolCall> toolCalls) {
        return new ModelResponse(Message.assistant(content), toolCalls, "stop", ModelUsage.zero(), Map.of());
    }

    private ModelResponse response(String content, List<ModelToolCall> toolCalls, BigDecimal estimatedCostUsd) {
        return new ModelResponse(Message.assistant(content), toolCalls, "stop", new ModelUsage(1, 1),
                Map.of("estimatedCostUsd", estimatedCostUsd));
    }

    /**
     * Test assertion helper: executes one terminal provider outcome and verifies its public failure code.
     */
    private void assertModelOutcomeFailure(ModelTurnOutcome outcome, String expectedErrorCode) {
        ModelResponse response = new ModelResponse(
                Message.assistant("provider did not complete"),
                List.of(),
                outcome.name(),
                outcome,
                ModelUsage.zero(),
                Map.of(),
                "test-provider",
                BigDecimal.ZERO);
        StaticProviderRegistry providers = new StaticProviderRegistry()
                .registerModel("model", new ScriptedModelProvider(List.of(response)));
        EmbeddedAgentRuntime runtime = new EmbeddedAgentRuntime(providers, new DefaultToolRegistry());
        AgentDefinition agent = new AgentDefinition(
                "agent",
                "1",
                Map.of(),
                List.of(phase("first", Map.of("prompt", "call model"), List.of(), 0)),
                List.of(),
                Map.of(),
                "model",
                List.of());

        AgentRunState run = runtime.start(agent, Map.of(), ExecutionContext.empty());

        assertThat(run.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(run.errorCode()).isEqualTo(expectedErrorCode);
        assertThat(run.phases().get("first").errorCode()).isEqualTo(expectedErrorCode);
    }

    private AgentDefinition twoPhaseAgent(BudgetPolicy budgetPolicy) {
        return new AgentDefinition(
                "agent",
                "1",
                Map.of(),
                List.of(phase("first", Map.of("prompt", "first"), List.of(), 0),
                        phase("second", Map.of("prompt", "second"), List.of(), 0)),
                List.of(),
                Map.of(),
                "model",
                List.of(),
                budgetPolicy
        );
    }

    private ExecutionContext withOuterBudget(BigDecimal budget) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(CostGuardrailContext.OUTER_BUDGET_REMAINING_USD, budget);
        return new ExecutionContext(null, null, List.of(), List.of(), null, null, Map.of(), metadata);
    }

    /**
     * Test ModelProvider: throws a wrapped provider exception carrying model detail and a fake
     * credential so the runtime's public failure redaction is exercised.
     */
    private static final class ThrowingModelProvider implements ModelProvider {
        /**
         * ModelProvider port implementation: simulates a provider SDK failure from a configured
         * model binding without making a network call.
         */
        @Override
        public ModelResponse complete(com.unfurl.foundry.substrate.model.ModelRequest request,
                                      ExecutionContext context) {
            throw new IllegalStateException("Failed to generate content",
                    new IllegalArgumentException("models/gemini-3.1-flash-lite missing apiKey="
                            + fakeProviderApiKey()));
        }
    }

    /**
     * Test ModelProvider: requests one tool call, then captures the JSON TOOL
     * message that the runtime sends back before returning a terminal response.
     */
    private static final class CapturingToolResultModelProvider implements ModelProvider {
        private int calls;
        private String toolMessageContent = "";

        /**
         * ModelProvider port implementation: drives a two-turn tool loop and
         * records the runtime-produced TOOL message for serialization assertions.
         */
        @Override
        public ModelResponse complete(com.unfurl.foundry.substrate.model.ModelRequest request,
                                      ExecutionContext context) {
            calls++;
            if (calls == 1) {
                return new ModelResponse(
                        Message.assistant("lookup"),
                        List.of(new ModelToolCall("call-1", "lookup", Map.of("query", "x"))),
                        "tool_call",
                        ModelUsage.zero(),
                        Map.of());
            }
            toolMessageContent = request.messages().stream()
                    .filter(message -> message.role() == MessageRole.TOOL)
                    .map(Message::content)
                    .findFirst()
                    .orElse("");
            return new ModelResponse(Message.assistant("{\"kind\":\"execution\"}"), List.of(), "stop",
                    ModelUsage.zero(), Map.of());
        }

        /**
         * Test accessor: returns the captured TOOL message content after the
         * runtime has performed the second model invocation.
         */
        private String toolMessageContent() {
            return toolMessageContent;
        }
    }

    /**
     * Test fixture helper: creates a provider-shaped credential at runtime so
     * redaction coverage does not require committing a key-shaped literal.
     */
    private static String fakeProviderApiKey() {
        return String.join("", "AI", "za", "Sy", "Fake", "Secret", "Value", "1234567890");
    }
}
