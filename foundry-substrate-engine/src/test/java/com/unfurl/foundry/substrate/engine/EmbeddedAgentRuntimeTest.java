package com.unfurl.foundry.substrate.engine;

import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.agent.AgentPhase;
import com.unfurl.foundry.substrate.agent.BudgetPolicy;
import com.unfurl.foundry.substrate.guardrail.CostGuardrailContext;
import com.unfurl.foundry.substrate.model.Message;
import com.unfurl.foundry.substrate.model.ModelResponse;
import com.unfurl.foundry.substrate.model.ModelToolCall;
import com.unfurl.foundry.substrate.model.ModelUsage;
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
}
