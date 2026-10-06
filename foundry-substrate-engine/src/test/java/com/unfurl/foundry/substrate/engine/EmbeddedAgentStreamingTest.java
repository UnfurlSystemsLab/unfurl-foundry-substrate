package com.unfurl.foundry.substrate.engine;

import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.agent.AgentDefinitionValidator;
import com.unfurl.foundry.substrate.agent.AgentPhase;
import com.unfurl.foundry.substrate.guardrail.BudgetPolicyCostGuardrail;
import com.unfurl.foundry.substrate.model.Message;
import com.unfurl.foundry.substrate.model.ModelDelta;
import com.unfurl.foundry.substrate.model.ModelRequest;
import com.unfurl.foundry.substrate.model.ModelResponse;
import com.unfurl.foundry.substrate.model.ModelUsage;
import com.unfurl.foundry.substrate.ports.CorrectionProgressObserver;
import com.unfurl.foundry.substrate.ports.ModelRequestProjector;
import com.unfurl.foundry.substrate.ports.ModelStreamObserver;
import com.unfurl.foundry.substrate.ports.StreamingModelProvider;
import com.unfurl.foundry.substrate.prompt.PromptAssembler;
import com.unfurl.foundry.substrate.resolver.DataReferenceResolver;
import com.unfurl.foundry.substrate.runstate.AgentRunStatus;
import com.unfurl.foundry.substrate.testing.StaticProviderRegistry;
import com.unfurl.foundry.substrate.tools.DefaultToolRegistry;
import com.unfurl.substrate.policy.ExecutionContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Streaming conformance for the agent runtime: deltas reach a bound observer with exact run/phase/call identity while the provider's
 * returned response stays authoritative; observer failures never change a run; without an observer the runtime keeps calling
 * {@code complete}.
 */
class EmbeddedAgentStreamingTest {
    /** Deltas are reported in order with their identity, and the run's output comes from the authoritative final response. */
    @Test void reportsDeltasWithIdentityAndKeepsTheResponseAuthoritative() {
        var observed = new CopyOnWriteArrayList<String>();
        var provider = new ScriptedStreamingProvider(List.of("Hel", "lo"));
        var run = runtime(provider, (runId, phaseId, callId, delta, context) ->
                observed.add(runId + "|" + phaseId + "|" + (callId != null && !callId.isBlank()) + "|" + delta.index() + "|" + delta.text()))
                .start(agent(), Map.of(), ExecutionContext.empty());
        assertThat(run.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(observed).containsExactly(run.runId() + "|answer|true|0|Hel", run.runId() + "|answer|true|1|lo");
        assertThat(run.phases().get("answer").output()).containsEntry("content", "Hello");
        assertThat(provider.completeCalls).hasValue(0);
    }

    /** An observer that throws cannot fail the run or alter its output. */
    @Test void isolatesObserverFailures() {
        var run = runtime(new ScriptedStreamingProvider(List.of("a", "b")), (runId, phaseId, callId, delta, context) -> {
            throw new IllegalStateException("projection outage");
        }).start(agent(), Map.of(), ExecutionContext.empty());
        assertThat(run.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(run.phases().get("answer").output()).containsEntry("content", "ab");
    }

    /** Without a bound observer the runtime uses complete even for a streaming-capable provider. */
    @Test void usesCompleteWithoutAnObserver() {
        var provider = new ScriptedStreamingProvider(List.of("x"));
        var run = runtime(provider, null).start(agent(), Map.of(), ExecutionContext.empty());
        assertThat(run.status()).isEqualTo(AgentRunStatus.COMPLETED);
        assertThat(provider.completeCalls).hasValue(1);
        assertThat(provider.streamCalls).hasValue(0);
    }

    /** Fixture: a fully wired runtime with the given provider and optional stream observer. */
    private static EmbeddedAgentRuntime runtime(StreamingModelProvider provider, ModelStreamObserver observer) {
        return new EmbeddedAgentRuntime(new StaticProviderRegistry().registerModel("model", provider), new DefaultToolRegistry(), null,
                new BudgetPolicyCostGuardrail(), new AllowAllPermissionBridge(), null, new PromptAssembler(), new DataReferenceResolver(),
                new AgentDefinitionValidator(), null, Map.of(), null, null, null, ExternalCallBoundary.direct(), ModelRequestProjector.identity(),
                CorrectionProgressObserver.noop(), observer);
    }

    /** Fixture: one phase with no tools that answers from the model's text. */
    private static AgentDefinition agent() {
        return new AgentDefinition("agent", "1", Map.of(), List.of(new AgentPhase("answer", null, null, List.of(), null,
                Map.of("prompt", "answer"), Map.of(), List.of(), 0)), List.of(), Map.of(), "model", List.of());
    }

    /** Test Double: a streaming provider that emits scripted fragments and returns their concatenation as the authoritative response. */
    private static final class ScriptedStreamingProvider implements StreamingModelProvider {
        private final List<String> fragments;
        final AtomicInteger completeCalls = new AtomicInteger();
        final AtomicInteger streamCalls = new AtomicInteger();

        /** Constructor: the fragments to stream. */
        ScriptedStreamingProvider(List<String> fragments) { this.fragments = fragments; }

        /** Blocking call: the whole answer at once. */
        @Override public ModelResponse complete(ModelRequest request, ExecutionContext context) {
            completeCalls.incrementAndGet();
            return response();
        }

        /** Streaming call: each fragment as an ordered delta, then the authoritative response. */
        @Override public ModelResponse stream(ModelRequest request, ExecutionContext context, Consumer<ModelDelta> deltas) {
            streamCalls.incrementAndGet();
            for (int index = 0; index < fragments.size(); index++) deltas.accept(new ModelDelta(index, fragments.get(index)));
            return response();
        }

        /** Response Factory: the concatenated text. */
        private ModelResponse response() {
            return new ModelResponse(Message.assistant(String.join("", fragments)), List.of(), "stop", ModelUsage.zero(), Map.of());
        }
    }
}
