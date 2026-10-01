package com.unfurl.foundry.substrate.engine;

import com.unfurl.foundry.substrate.agent.*;
import com.unfurl.foundry.substrate.model.*;
import com.unfurl.foundry.substrate.ports.*;
import com.unfurl.foundry.substrate.runstate.*;
import com.unfurl.foundry.substrate.testing.*;
import com.unfurl.foundry.substrate.tools.DefaultToolRegistry;
import com.unfurl.substrate.policy.ExecutionContext;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** Conformance fixture: verifies the neutral continuation seam without pretending to own host claims or grants. */
class EmbeddedAgentToolContinuationTest {
    /** Restart contract: preserves the saved transcript and consumes only remaining tool/model work. */
    @Test void restartsSharedLoopWithoutRepeatingInitialWork() {
        var f = new Fixture(2);
        var wait = f.start();
        var runtime = f.runtime(List.of(reply("done", List.of())));
        var completed = runtime.continueTool(f.agent, wait, (pending, request, context) -> {
            assertThat(request.arguments()).containsEntry("value", "normalized");
            assertThat(ToolCallScope.from(request.metadata()).toolCallId()).isEqualTo(pending.toolCallId());
            return ToolCallResult.success(Map.of("raw", true));
        }, ExecutionContext.empty());
        assertThat(completed.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(completed.runId()).isEqualTo(wait.runId());
        assertThat(completed.createdAt()).isEqualTo(wait.createdAt());
        assertThat(completed.agentInput()).isEqualTo(wait.agentInput());
        var phase = completed.phases().get("first");
        assertThat(phase.startedAt()).isEqualTo(wait.phases().get("first").startedAt());
        assertThat(phase.toolCalls()).extracting(ToolCall::callId).containsExactly("before", "pending", "after");
        assertThat(phase.toolCalls().get(1).result()).isEmpty();
        assertThat(phase.messages()).filteredOn(m -> m.role() == MessageRole.ASSISTANT && m.content().equals("batch")).hasSize(1);
        assertThat(f.before).hasValue(3);
        assertThat(f.after).hasValue(3);
        assertThat(f.lookup.calls()).hasSize(2);
        assertThat(completed.cost()).isEqualTo(wait.cost());
        assertThat(completed.cost().estimatedCostUsd()).isEqualByComparingTo("0.25");
        assertThatThrownBy(() -> runtime.continueTool(f.agent, wait, (p, r, c) -> {
            throw new AssertionError("stale effect");
        }, ExecutionContext.empty())).isInstanceOf(IllegalStateException.class);
    }

    /** Repeated-wait contract: a later normal call may suspend and retain a fresh original-attempt identity. */
    @Test void suspendsAgainThenContinuesWithoutLosingEarlierObservations() {
        var f = new Fixture(2);
        var first = f.start();
        f.suspendLookup = true;
        var second = f.runtime(List.of()).continueTool(f.agent, first,
                (p, r, c) -> ToolCallResult.emptySuccess(), ExecutionContext.empty());
        assertThat(second.status()).isEqualTo(AgentRunStatus.WAITING);
        var phase = second.phases().get("first");
        assertThat(phase.toolCalls()).extracting(ToolCall::callId).containsExactly("before", "pending");
        assertThat(phase.suspension().providerCallId()).isEqualTo("after");
        assertThat(phase.suspension().toolCallId()).isNotEqualTo(first.phases().get("first").suspension().toolCallId());
        assertThat(phase.startedAt()).isEqualTo(first.phases().get("first").startedAt());
        var done = f.runtime(List.of(reply("done", List.of()))).continueTool(f.agent, second,
                (p, r, c) -> ToolCallResult.emptySuccess(), ExecutionContext.empty());
        assertThat(done.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(done.phases().get("first").toolCalls()).hasSize(3);
        assertThat(f.lookup.calls()).hasSize(1);
    }

    /** Uncertainty contract: callback exceptions leave the saved wait untouched and never dispatch residual work. */
    @Test void leavesWaitIntactOnUncertainHostResponse() {
        var f = new Fixture(2);
        var wait = f.start();
        assertThatThrownBy(() -> f.runtime(List.of()).continueTool(f.agent, wait, (p, r, c) -> {
            throw new IllegalStateException("uncertain receipt");
        }, ExecutionContext.empty())).hasMessage("uncertain receipt");
        assertThat(f.store.load(wait.runId(), ExecutionContext.empty())).contains(wait);
        assertThat(f.lookup.calls()).hasSize(1);
    }

    /** Failure contract: expected raw failures pass through result policy and retain completed observations. */
    @Test void preservesObservationsWhenPendingToolFails() {
        var f = new Fixture(2);
        var done = f.runtime(List.of()).continueTool(f.agent, f.start(),
                (p, r, c) -> ToolCallResult.failure("EXPECTED", "refused"), ExecutionContext.empty());
        assertThat(done.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(done.phases().get("first").errorCode()).isEqualTo("EXPECTED");
        assertThat(done.phases().get("first").toolCalls()).extracting(ToolCall::callId).containsExactly("before", "pending");
        assertThat(f.lookup.calls()).hasSize(1);
    }

    /** Finite-loop contract: continuation restores the bound rather than granting a fresh tool-loop budget. */
    @Test void enforcesOriginalIterationLimitAndRetainsObservations() {
        var f = new Fixture(1);
        var wait = f.start();
        var done = f.runtime(List.of(reply("more", List.of(new ModelToolCall("extra", "lookup", Map.of())))))
                .continueTool(f.agent, wait, (p, r, c) -> ToolCallResult.emptySuccess(), ExecutionContext.empty());
        assertThat(done.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(done.phases().get("first").errorCode()).isEqualTo("MAX_TOOL_ITERATIONS_EXCEEDED");
        assertThat(done.phases().get("first").toolCalls()).hasSize(3);
        assertThat(f.lookup.calls()).hasSize(2);
    }

    /** Host-boundary contract: cancelled, drifted and missing-executor requests cannot execute the retained attempt. */
    @Test void rejectsInvalidContinuationBeforeEffects() {
        var f = new Fixture(2);
        var wait = f.start();
        var runtime = f.runtime(List.of());
        assertThat(runtime.resume(wait.runId(), Map.of("approved", true), ExecutionContext.empty())).isEqualTo(wait);
        assertThatThrownBy(() -> runtime.continueTool(f.agent, wait, null, ExecutionContext.empty())).isInstanceOf(NullPointerException.class);
        var drift = new AgentDefinition("agent", "2", Map.of(), f.agent.phases(), List.of(), Map.of(), "model", List.of("lookup", "write"));
        assertThatThrownBy(() -> runtime.continueTool(drift, wait, (p, r, c) -> {
            throw new AssertionError("drift effect");
        }, ExecutionContext.empty())).isInstanceOf(IllegalArgumentException.class);
        var cancelled = runtime.cancel(wait.runId(), ExecutionContext.empty());
        assertThatThrownBy(() -> runtime.continueTool(f.agent, cancelled, (p, r, c) -> {
            throw new AssertionError("cancelled effect");
        }, ExecutionContext.empty())).isInstanceOf(IllegalArgumentException.class);
    }

    /** Admission contract: tightened tool permissions reject before the host strategy and preserve saved observations. */
    @Test void rechecksPermissionBeforeRetainedDispatch() {
        var f = new Fixture(2);
        var wait = f.start();
        f.denyPermission = true;
        var done = f.runtime(List.of()).continueTool(f.agent, wait, (p, r, c) -> {
            throw new AssertionError("denied effect");
        }, ExecutionContext.empty());
        assertThat(done.phases().get("first").errorCode()).isEqualTo("PERMISSION_DENIED");
        assertThat(done.phases().get("first").toolCalls()).hasSize(1);
    }

    /** Budget contract: the current saved cost is checked before any resumed tool/model dispatch. */
    @Test void rechecksGuardrailBeforeRetainedDispatch() {
        var f = new Fixture(2);
        var wait = f.start();
        f.denyBudget = true;
        var done = f.runtime(List.of()).continueTool(f.agent, wait, (p, r, c) -> {
            throw new AssertionError("budget effect");
        }, ExecutionContext.empty());
        assertThat(done.phases().get("first").errorCode()).isEqualTo("GUARDRAIL_TRIPPED");
        assertThat(done.phases().get("first").toolCalls()).hasSize(1);
    }

    /** Scheduler contract: successful continued work releases later phases through the ordinary scheduler. */
    @Test void completesLaterPhasesInTheSameRun() {
        var f = new Fixture(2);
        f.agent = new AgentDefinition("agent", "1", Map.of(), List.of(f.agent.phases().getFirst(),
                new AgentPhase("later", null, null, List.of(), null, Map.of("prompt", "later"), Map.of(), List.of(), 0)),
                List.of(), Map.of(), "model", List.of("lookup", "write"));
        var wait = f.start();
        assertThat(wait.phases().get("later").status()).isEqualTo(AgentPhaseStatus.PENDING);
        var done = f.runtime(List.of(reply("done", List.of()), reply("later done", List.of())))
                .continueTool(f.agent, wait, (p, r, c) -> ToolCallResult.emptySuccess(), ExecutionContext.empty());
        assertThat(done.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(done.phases().get("later").status()).isEqualTo(AgentPhaseStatus.COMPLETED);
    }

    /** Mapping contract: retained and residual observations complete deterministic mapping without another model turn. */
    @Test void completesMappedOutputWithoutFollowUpModel() {
        var f = new Fixture(2);
        f.configurePhase(Map.of("found", "$.tools.after.output.found"), null, null);
        var wait = f.start();
        var done = f.runtime(List.of()).continueTool(f.agent, wait,
                (p, r, c) -> ToolCallResult.emptySuccess(), ExecutionContext.empty());
        assertThat(done.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(done.phases().get("first").output()).containsEntry("found", true);
    }

    /** Validation contract: normal bounded correction applies after continuation and retains all tool observations. */
    @Test void performsBoundedOutputCorrectionAfterContinuation() {
        var f = new Fixture(2);
        f.configurePhase(Map.of(), "schema", new CorrectionPolicy(1, 60000, CorrectionExhaustionAction.FAIL, Map.of()));
        var wait = f.start();
        var done = f.runtime(List.of(reply("invalid", List.of()), reply("{\"amount\":2}", List.of())))
                .continueTool(f.agent, wait, (p, r, c) -> ToolCallResult.emptySuccess(), ExecutionContext.empty());
        assertThat(done.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(done.phases().get("first").output()).containsEntry("amount", 2);
        assertThat(done.phases().get("first").toolCalls()).hasSize(3);
    }

    /** Model-failure contract: a failed follow-up turn cannot erase already executed tool observations. */
    @Test void preservesObservationsWhenFollowUpModelFails() {
        var f = new Fixture(2);
        var wait = f.start();
        var done = f.runtime(List.of()).continueTool(f.agent, wait,
                (p, r, c) -> ToolCallResult.emptySuccess(), ExecutionContext.empty());
        assertThat(done.status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(done.phases().get("first").toolCalls()).hasSize(3);
    }

    /** Factory: builds exact scripted responses so unexpected initial or extra model turns fail loudly. */
    private static ModelResponse reply(String text, List<ModelToolCall> calls) {
        return new ModelResponse(Message.assistant(text), calls, "stop", ModelUsage.zero(), Map.of());
    }

    /** Fixture Builder: shares only the saved run store and policy counters across independent engine instances. */
    private static final class Fixture {
        final InMemoryAgentRunStore store = new InMemoryAgentRunStore();
        final RecordingToolExecutor lookup = new RecordingToolExecutor(Map.of("found", true));
        final AtomicInteger before = new AtomicInteger(), after = new AtomicInteger();
        AgentDefinition agent;
        boolean suspendLookup, denyPermission, denyBudget;
        /** Definition Factory: pins a single finite-loop phase with a normalized write admission. */
        Fixture(int bound) {
            agent = new AgentDefinition("agent", "1", Map.of(), List.of(new AgentPhase("first", null, null,
                    List.of("lookup", "write"), null, Map.of("prompt", "batch"), Map.of(), List.of(), bound)),
                    List.of(), Map.of(), "model", List.of("lookup", "write"));
        }
        /** Definition Builder: changes the pinned pre-start validation/mapping policy, never a saved execution plan. */
        void configurePhase(Map<String, Object> mapping, String schema, CorrectionPolicy correction) {
            var phase = new AgentPhase("first", null, null, List.of("lookup", "write"), null,
                    Map.of("prompt", "batch"), mapping, List.of(), 2, List.of(), schema, null, correction);
            agent = new AgentDefinition("agent", "1", Map.of(), List.of(phase), List.of(), Map.of(), "model", List.of("lookup", "write"));
        }
        /** Start Factory: captures a genuine mid-batch wait after one completed observation. */
        AgentRunState start() {
            var batch = new ModelResponse(Message.assistant("batch"), List.of(new ModelToolCall("before", "lookup", Map.of()),
                    new ModelToolCall("pending", "write", Map.of("value", "raw")),
                    new ModelToolCall("after", "lookup", Map.of())), "tool_use", new ModelUsage(5, 7),
                    Map.of("estimatedCostUsd", new java.math.BigDecimal("0.25")));
            return runtime(List.of(batch)).start(agent, Map.of("source", "original"), ExecutionContext.empty());
        }
        /** Runtime Factory: intentionally forbids physical write dispatch and transforms raw write results after admission. */
        EmbeddedAgentRuntime runtime(List<ModelResponse> responses) {
            var tools = new DefaultToolRegistry();
            tools.register("lookup", lookup);
            tools.register("write", (r, c) -> { throw new AssertionError("physical write replay"); });
            var policy = new ToolCallInterceptor() {
                /** Policy Strategy: normal calls alone are admitted; retained calls reuse saved normalization. */
                @Override public ToolCallDecision before(ToolCallRequest request, ExecutionContext context) {
                    before.incrementAndGet();
                    return request.toolName().equals("write") || suspendLookup
                            ? ToolCallDecision.requireApproval(Map.of("value", "normalized"), "approval", Map.of())
                            : ToolCallDecision.allow(request.arguments());
                }
                /** Result Strategy: demonstrates raw receipt hydration still traverses the existing result policy. */
                @Override public ToolCallResult after(ToolCallRequest request, ToolCallResult result, ExecutionContext context) {
                    after.incrementAndGet();
                    return request.toolName().equals("write") && result.success()
                            ? ToolCallResult.emptySuccess() : result;
                }
            };
            return new EmbeddedAgentRuntime(new StaticProviderRegistry().registerModel("model", new ScriptedModelProvider(responses)),
                    tools, null, (cost, context) -> denyBudget
                        ? com.unfurl.foundry.substrate.guardrail.GuardrailDecision.deny("budget tightened")
                        : new com.unfurl.foundry.substrate.guardrail.BudgetPolicyCostGuardrail().check(cost, context),
                    (tool, args, context) -> denyPermission
                        ? com.unfurl.foundry.substrate.guardrail.PermissionDecision.deny("permission tightened")
                        : com.unfurl.foundry.substrate.guardrail.PermissionDecision.allow(),
                    null, new com.unfurl.foundry.substrate.prompt.PromptAssembler(),
                    new com.unfurl.foundry.substrate.resolver.DataReferenceResolver(), new AgentDefinitionValidator(),
                    store, Map.of(), new ToolCallInterceptorChain(List.of(policy)),
                    (schema, value, context) -> value instanceof Map<?, ?> map && map.get("amount") instanceof Number
                        ? ValidationResult.success() : ValidationResult.invalid(List.of(new ValidationIssue("AMOUNT_REQUIRED", "$.amount", "amount required", Map.of()))),
                    null);
        }
    }
}
