package com.unfurl.foundry.substrate.serialization;

import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.agent.AgentHarnessDefinition;
import com.unfurl.foundry.substrate.agent.AgentHarnessLoopPolicy;
import com.unfurl.foundry.substrate.agent.AgentPhase;
import com.unfurl.foundry.substrate.failure.FailureCategory;
import com.unfurl.foundry.substrate.failure.StructuredFailure;
import com.unfurl.foundry.substrate.skill.SkillDefinition;
import com.unfurl.foundry.substrate.model.Message;
import com.unfurl.foundry.substrate.model.ModelResponse;
import com.unfurl.foundry.substrate.model.ModelTurnOutcome;
import com.unfurl.foundry.substrate.model.ModelUsage;
import com.unfurl.foundry.substrate.tool.ToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FoundrySubstrateCodecTest {
    private final FoundrySubstrateCodec codec = new FoundrySubstrateCodec();

    @Test
    void roundTripsAgentDefinitionThroughYaml() {
        String yaml = """
                id: support-agent
                version: 1
                metadata:
                  modelRefs: [model]
                  promptTemplateRefs: [triage]
                phases:
                  - id: triage
                    promptTemplateRef: triage
                    modelRef: model
                    input:
                      prompt: $.agent.input.prompt
                    maxToolIterations: 0
                edges: []
                inputSchema: {}
                defaultModelRef: model
                toolRefs: []
                """;

        AgentDefinition agent = codec.fromYaml(yaml, AgentDefinition.class);
        AgentDefinition reloaded = codec.fromJson(codec.toJson(agent), AgentDefinition.class);

        assertThat(reloaded.id()).isEqualTo("support-agent");
        assertThat(reloaded.phases()).hasSize(1);
        assertThat(reloaded.phases().getFirst().promptTemplateRef()).isEqualTo("triage");
        assertThat(reloaded.skillRefs()).isEmpty();
    }

    @Test
    void roundTripsToolDefinitionThroughJson() {
        ToolDefinition tool = new ToolDefinition("search", "1", "Search", Map.of("type", "object"),
                Map.of("type", "object"), "lookup", Map.of("owner", "test"));

        ToolDefinition reloaded = codec.fromJson(codec.toJson(tool), ToolDefinition.class);

        assertThat(reloaded.description()).isEqualTo("Search");
        assertThat(reloaded.uses()).isEqualTo("lookup");
        assertThat(reloaded.metadata()).containsEntry("owner", "test");
    }

    @Test
    void roundTripsSkillDefinitionThroughJsonAndYaml() {
        SkillDefinition skill = new SkillDefinition("research", "1.0.0", "Research",
                List.of("search"), "research-prompt", List.of("docs"), "model",
                null, List.of("tools.search"), Map.of("type", "object"), Map.of(), Map.of("owner", "test"));

        SkillDefinition fromJson = codec.fromJson(codec.toJson(skill), SkillDefinition.class);
        SkillDefinition fromYaml = codec.fromYaml(codec.toYaml(skill), SkillDefinition.class);

        assertThat(fromJson).isEqualTo(skill);
        assertThat(fromYaml).isEqualTo(skill);
    }

    @Test
    void roundTripsAdditiveAgentAndPhaseSkillRefs() {
        AgentDefinition agent = new AgentDefinition("support-agent", "1.0.0", Map.of(),
                List.of(new AgentPhase("triage", null, null, List.of(), null, Map.of(), Map.of(), List.of(),
                        0, List.of("triage@1.0.0"))),
                List.of(), Map.of(), null, List.of(), null, List.of("common@1.0.0"));

        AgentDefinition reloaded = codec.fromJson(codec.toJson(agent), AgentDefinition.class);

        assertThat(reloaded.skillRefs()).containsExactly("common@1.0.0");
        assertThat(reloaded.phases().getFirst().skillRefs()).containsExactly("triage@1.0.0");
    }

    @Test
    void roundTripsAgentHarnessDefinitionThroughJson() {
        AgentDefinition agent = new AgentDefinition("authoring-agent", "1.0.0", Map.of(),
                List.of(new AgentPhase("turn", null, null, List.of(), null, Map.of(), Map.of(), List.of(), 0)),
                List.of(), Map.of(), null, List.of());
        AgentHarnessDefinition harness = new AgentHarnessDefinition("authoring-harness", "1.0.0",
                Map.of("owner", "foundry"), agent, new AgentHarnessLoopPolicy(4, 0, Map.of()));

        AgentHarnessDefinition reloaded = codec.fromJson(codec.toJson(harness), AgentHarnessDefinition.class);

        assertThat(reloaded.id()).isEqualTo("authoring-harness");
        assertThat(reloaded.agent().id()).isEqualTo("authoring-agent");
        assertThat(reloaded.loopPolicy().maxTurns()).isEqualTo(4);
        assertThat(reloaded.metadata()).containsEntry("owner", "foundry");
    }

    /** Public-record compatibility: serializes the neutral outcome while retaining the legacy reason. */
    @Test
    void roundTripsModelResponseWithNeutralOutcome() {
        ModelResponse response = new ModelResponse(
                Message.assistant("done"), List.of(), "end_turn", ModelTurnOutcome.COMPLETED,
                new ModelUsage(3, 5), Map.of("providerName", "test"), "test", null);

        String json = codec.toJson(response);
        ModelResponse reloaded = codec.fromJson(json, ModelResponse.class);

        assertThat(json).contains("\"finishReason\"", "\"outcome\"");
        assertThat(reloaded.outcome()).isEqualTo(ModelTurnOutcome.COMPLETED);
        assertThat(reloaded.finishReason()).isEqualTo("end_turn");
    }

    /**
     * Public-record compatibility: round-trips retry policy, partial output, details, and provenance.
     */
    @Test
    void roundTripsStructuredFailure() {
        StructuredFailure failure = new StructuredFailure(
                "TOOL_RATE_LIMITED", FailureCategory.RATE_LIMIT, true, 500L, "retry later",
                Map.of("accepted", 1), Map.of("limit", 2), Map.of("tool", "catalog"));

        StructuredFailure reloaded = codec.fromYaml(codec.toYaml(failure), StructuredFailure.class);

        assertThat(reloaded).isEqualTo(failure);
    }
}
