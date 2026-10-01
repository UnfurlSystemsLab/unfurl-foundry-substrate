package com.unfurl.foundry.substrate.engine;

import com.unfurl.foundry.substrate.agent.*;
import com.unfurl.foundry.substrate.guardrail.BudgetPolicyCostGuardrail;
import com.unfurl.foundry.substrate.model.*;
import com.unfurl.foundry.substrate.ports.*;
import com.unfurl.foundry.substrate.prompt.PromptAssembler;
import com.unfurl.foundry.substrate.resolver.DataReferenceResolver;
import com.unfurl.foundry.substrate.runstate.AgentRunStatus;
import com.unfurl.foundry.substrate.testing.StaticProviderRegistry;
import com.unfurl.foundry.substrate.tools.DefaultToolRegistry;
import com.unfurl.substrate.policy.ExecutionContext;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;

/** Port conformance tests: projection precedes the physical boundary on initial, tool, and correction calls. */
class ModelRequestProjectionTest {
    /** Verifies both turns in a tool loop deliver exactly the projected request to boundary and provider. */
    @Test void projectsInitialAndToolFollowUpRequests() {
        List<ModelRequest> received = new ArrayList<>();
        List<ModelRequest> journaled = new ArrayList<>();
        var providers = new StaticProviderRegistry().registerModel("model", (request, context) -> {
            received.add(request);
            return new ModelResponse(Message.assistant("ok"), received.size() == 1
                    ? List.of(new ModelToolCall("call", "lookup", Map.of())) : List.of(), "stop", ModelUsage.zero(), Map.of());
        });
        var tools = new DefaultToolRegistry();
        tools.register("lookup", (request, context) -> ToolCallResult.success(Map.of("found", true)));
        var phase = new AgentPhase("phase", null, "model", List.of("lookup"), null,
                Map.of("prompt", "work"), Map.of(), List.of(), 1);
        var agent = new AgentDefinition("child", "1", Map.of(), List.of(phase), List.of(), Map.of(), "model", List.of("lookup"));
        var runtime = runtime(providers, tools, null, journaled, projection());
        assertThat(runtime.start(agent, Map.of("objective", "original"), ExecutionContext.empty()).status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(received).hasSize(2).isEqualTo(journaled);
        assertThat(received).allSatisfy(request -> assertThat(request.metadata()).containsEntry("projected", true));
        assertThat(received.getLast().messages()).anyMatch(message -> message.role() == MessageRole.TOOL);
    }

    /** Verifies validation correction requests cannot bypass the same projection and durable boundary. */
    @Test void projectsCorrectionRequests() {
        List<ModelRequest> received = new ArrayList<>();
        List<ModelRequest> journaled = new ArrayList<>();
        var providers = new StaticProviderRegistry().registerModel("model", (request, context) -> {
            received.add(request);
            return new ModelResponse(Message.assistant(received.size() == 1 ? "{\"amount\":\"bad\"}" : "{\"amount\":2}"),
                    List.of(), "stop", ModelUsage.zero(), Map.of());
        });
        var phase = new AgentPhase("phase", null, "model", List.of(), null, Map.of("prompt", "amount"),
                Map.of("amount", "$.output.amount"), List.of(), 0, List.of(), "schema", null,
                new CorrectionPolicy(1, 5000, CorrectionExhaustionAction.FAIL, Map.of()));
        var agent = new AgentDefinition("child", "1", Map.of(), List.of(phase), List.of(), Map.of(), "model", List.of());
        OutputSchemaValidator validator = (ref, value, context) -> value instanceof Map<?, ?> map && map.get("amount") instanceof Number
                ? ValidationResult.success() : ValidationResult.invalid(List.of(new ValidationIssue("AMOUNT", "$.amount", "numeric amount required", Map.of())));
        assertThat(runtime(providers, new DefaultToolRegistry(), validator, journaled, projection())
                .start(agent, Map.of(), ExecutionContext.empty()).status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(received).hasSize(2).isEqualTo(journaled);
        assertThat(received.getLast().metadata()).containsEntry("projected", true);
    }

    /** Verifies projection failure creates no external intent and never invokes a provider. */
    @Test void rejectsBeforeExternalBoundary() {
        var calls = new AtomicInteger();
        List<ModelRequest> journaled = new ArrayList<>();
        var providers = new StaticProviderRegistry().registerModel("model", (request, context) -> { calls.incrementAndGet(); throw new AssertionError(); });
        var phase = new AgentPhase("phase", null, "model", List.of(), null, Map.of("prompt", "work"), Map.of(), List.of(), 0);
        var agent = new AgentDefinition("child", "1", Map.of(), List.of(phase), List.of(), Map.of(), "model", List.of());
        var runtime = runtime(providers, new DefaultToolRegistry(), null, journaled,
                (definition, selectedPhase, invocation, input, request, context) -> { throw new IllegalArgumentException("context rejected"); });
        assertThat(runtime.start(agent, Map.of(), ExecutionContext.empty()).status()).isEqualTo(AgentRunStatus.FAILED);
        assertThat(calls).hasValue(0);
        assertThat(journaled).isEmpty();
    }

    /** Fixture Strategy: marks the final request so equality proves no unprojected request bypassed the boundary. */
    private ModelRequestProjector projection() {
        return (agent, phase, invocation, input, request, context) -> {
            Map<String, Object> metadata = new LinkedHashMap<>(request.metadata());
            metadata.put("projected", true);
            return new ModelRequest(request.messages(), request.modelRef(), request.parameters(), request.toolSchemas(), metadata);
        };
    }

    /** Fixture Factory: injects only neutral ports, capturing the exact request at the physical call boundary. */
    private EmbeddedAgentRuntime runtime(ProviderRegistry providers, ToolRegistry tools, OutputSchemaValidator validator,
                                         List<ModelRequest> journaled, ModelRequestProjector projector) {
        ExternalCallBoundary boundary = new ExternalCallBoundary() {
            /** Recording Strategy: observes provider intents before invoking the supplied physical operation once. */
            @Override public <T> T invoke(Call call, Supplier<T> invocation) {
                if (call.kind() == Kind.PROVIDER) {
                    ModelRequest request = (ModelRequest) call.request();
                    assertThat(request.metadata()).containsEntry("agentRunId", call.runId()).containsEntry("modelCallId", call.callId());
                    assertThat(request.metadata()).containsEntry("phaseId", "phase");
                    journaled.add(request);
                }
                return invocation.get();
            }
        };
        return new EmbeddedAgentRuntime(providers, tools, null, new BudgetPolicyCostGuardrail(), new AllowAllPermissionBridge(), null,
                new PromptAssembler(), new DataReferenceResolver(), new AgentDefinitionValidator(), null, Map.of(),
                ToolCallInterceptorChain.empty(), validator, null, boundary, projector);
    }
}
