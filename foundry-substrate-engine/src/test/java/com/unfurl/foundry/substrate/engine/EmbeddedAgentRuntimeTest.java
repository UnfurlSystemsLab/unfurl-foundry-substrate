package com.unfurl.foundry.substrate.engine;

import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.agent.AgentPhase;
import com.unfurl.foundry.substrate.agent.BudgetPolicy;
import com.unfurl.foundry.substrate.guardrail.CostGuardrailContext;
import com.unfurl.foundry.substrate.model.Message;
import com.unfurl.foundry.substrate.model.ModelResponse;
import com.unfurl.foundry.substrate.model.ModelToolCall;
import com.unfurl.foundry.substrate.model.ModelUsage;
import com.unfurl.foundry.substrate.model.MessageRole;
import com.unfurl.foundry.substrate.ports.ModelProvider;
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

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddedAgentRuntimeTest {

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

    private ModelResponse response(String content, List<ModelToolCall> toolCalls) {
        return new ModelResponse(Message.assistant(content), toolCalls, "stop", ModelUsage.zero(), Map.of());
    }

    private ModelResponse response(String content, List<ModelToolCall> toolCalls, BigDecimal estimatedCostUsd) {
        return new ModelResponse(Message.assistant(content), toolCalls, "stop", new ModelUsage(1, 1),
                Map.of("estimatedCostUsd", estimatedCostUsd));
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
}
