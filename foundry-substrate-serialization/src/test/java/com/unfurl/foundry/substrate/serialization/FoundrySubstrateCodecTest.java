package com.unfurl.foundry.substrate.serialization;

import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.tool.ToolDefinition;
import org.junit.jupiter.api.Test;

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
}
