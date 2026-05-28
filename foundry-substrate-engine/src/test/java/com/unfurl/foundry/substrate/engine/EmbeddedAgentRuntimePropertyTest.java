package com.unfurl.foundry.substrate.engine;

import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.agent.AgentPhase;
import com.unfurl.foundry.substrate.model.Message;
import com.unfurl.foundry.substrate.model.MessageRole;
import com.unfurl.foundry.substrate.model.ModelRequest;
import com.unfurl.foundry.substrate.model.ModelResponse;
import com.unfurl.foundry.substrate.model.ModelUsage;
import com.unfurl.foundry.substrate.ports.ModelProvider;
import com.unfurl.foundry.substrate.runstate.AgentPhaseStatus;
import com.unfurl.foundry.substrate.runstate.AgentRunState;
import com.unfurl.foundry.substrate.testing.StaticProviderRegistry;
import com.unfurl.foundry.substrate.tools.DefaultToolRegistry;
import com.unfurl.substrate.domain.ConditionDefinition;
import com.unfurl.substrate.domain.EdgeDefinition;
import com.unfurl.substrate.policy.ExecutionContext;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddedAgentRuntimePropertyTest {

    @Property
    void conditionalEdgeRoutingIsDeterministic(@ForAll boolean condition) {
        AgentRunState first = runBranchingAgent(condition);
        AgentRunState second = runBranchingAgent(condition);

        assertThat(second.phases().get("second").status()).isEqualTo(first.phases().get("second").status());
        assertThat(first.phases().get("second").status())
                .isEqualTo(condition ? AgentPhaseStatus.COMPLETED : AgentPhaseStatus.SKIPPED);
    }

    @Property
    void topologicalSchedulerOrderIsDeterministic(@ForAll("phaseCounts") int phaseCount) {
        RecordingProvider firstProvider = new RecordingProvider("ok");
        RecordingProvider secondProvider = new RecordingProvider("ok");
        AgentDefinition agent = linearAgent(phaseCount);

        runtime(firstProvider).start(agent, Map.of(), ExecutionContext.empty());
        runtime(secondProvider).start(agent, Map.of(), ExecutionContext.empty());

        assertThat(secondProvider.prompts()).isEqualTo(firstProvider.prompts());
        assertThat(firstProvider.prompts()).containsExactlyElementsOf(
                java.util.stream.IntStream.range(0, phaseCount).mapToObj(i -> "phase-" + i).toList());
    }

    @Provide
    Arbitrary<Integer> phaseCounts() {
        return Arbitraries.integers().between(1, 8);
    }

    private AgentRunState runBranchingAgent(boolean condition) {
        EmbeddedAgentRuntime runtime = runtime(new RecordingProvider(String.valueOf(condition), "done"));
        AgentDefinition agent = new AgentDefinition(
                "agent",
                "1",
                Map.of(),
                List.of(phase("first", "decide", List.of()), phase("second", "run", List.of())),
                List.of(new EdgeDefinition("first", "second", new ConditionDefinition("$.phases.first.output.content"))),
                Map.of(),
                "model",
                List.of());
        return runtime.start(agent, Map.of(), ExecutionContext.empty());
    }

    private AgentDefinition linearAgent(int phaseCount) {
        List<AgentPhase> phases = new ArrayList<>();
        for (int i = 0; i < phaseCount; i++) {
            phases.add(phase("phase-" + i, "phase-" + i, i == 0 ? List.of() : List.of("phase-" + (i - 1))));
        }
        return new AgentDefinition("agent", "1", Map.of(), phases, List.of(), Map.of(), "model", List.of());
    }

    private EmbeddedAgentRuntime runtime(ModelProvider provider) {
        return new EmbeddedAgentRuntime(new StaticProviderRegistry().registerModel("model", provider), new DefaultToolRegistry());
    }

    private AgentPhase phase(String id, String prompt, List<String> dependencies) {
        return new AgentPhase(id, null, null, List.of(), null, Map.of("prompt", prompt), Map.of(), dependencies, 0);
    }

    private static final class RecordingProvider implements ModelProvider {
        private final List<String> prompts = new ArrayList<>();
        private final List<String> responses;
        private int index;

        private RecordingProvider(String... responses) {
            this.responses = List.of(responses);
        }

        @Override
        public ModelResponse complete(ModelRequest request, ExecutionContext context) {
            prompts.add(request.messages().stream()
                    .filter(message -> message.role() == MessageRole.USER)
                    .map(Message::content)
                    .reduce((first, second) -> second)
                    .orElse(""));
            String response = responses.get(Math.min(index, responses.size() - 1));
            index++;
            return new ModelResponse(Message.assistant(response), List.of(), "stop", ModelUsage.zero(), Map.of());
        }

        private List<String> prompts() {
            return List.copyOf(prompts);
        }
    }
}
