package com.unfurl.foundry.substrate.resolver;

import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.agent.AgentPhase;
import com.unfurl.foundry.substrate.serialization.FoundrySubstrateCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies complete, deterministic projection of the pinned agentRef contract. */
class ResolvedAgentReferenceFactoryTest {
    private final ResolvedAgentReferenceFactory factory = new ResolvedAgentReferenceFactory();

    /** Projects schemas, harness policy, dependencies, governance, permissions, and DCP identity. */
    @Test
    void projectsCompleteResolvedContract() {
        AgentDefinition agent = new AgentDefinition("assistant", "1.0.0", Map.of(
                "inputSchemaRef", "schema:assistant-input@1",
                "terminalOutputSchemaRef", "schema:assistant-output@1",
                "harnessPolicy", Map.of("maxTurns", 4, "maxDurationMillis", 5000),
                "modelRefs", List.of("model-b"),
                "promptTemplateRefs", List.of("prompt-a"),
                "permissionConstraints", List.of("tools.lookup"),
                "governanceConstraints", Map.of("region", "IN"),
                "dcpBindingIdentity", Map.of("contractId", "urn:agent")),
                List.of(new AgentPhase("answer", "prompt-b", "model-a", List.of("lookup@1"),
                        "knowledge@1", Map.of(), Map.of(), List.of(), 0, List.of("skill-a@1"))),
                List.of(), Map.of(), "model-a", List.of("base-tool@1"), null, List.of("base-skill@1"));

        ResolvedAgentReference resolved = factory.create("assistant@1.0.0", "assistant", "1.0.0", agent);

        assertThat(resolved.definitionDigest()).hasSize(64);
        assertThat(resolved.inputSchemaRef()).isEqualTo("schema:assistant-input@1");
        assertThat(resolved.terminalOutputSchemaRef()).isEqualTo("schema:assistant-output@1");
        assertThat(resolved.harnessPolicy().maxTurns()).isEqualTo(4);
        assertThat(resolved.dependencyProvenance().get("tools"))
                .containsExactly("base-tool@1", "lookup@1");
        assertThat(resolved.dependencyProvenance().get("models"))
                .containsExactly("model-a", "model-b");
        assertThat(resolved.permissionConstraints()).containsExactly("tools.lookup");
        assertThat(resolved.dcpBindingIdentity()).containsEntry("contractId", "urn:agent");
    }

    /** Rejects registry results whose identity differs from the pinned reference. */
    @Test
    void rejectsVersionMismatch() {
        AgentDefinition agent = new AgentDefinition("assistant", "2.0.0", Map.of(), List.of(), List.of(),
                Map.of(), null, List.of());

        assertThatThrownBy(() -> factory.create("assistant@1.0.0", "assistant", "1.0.0", agent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match pinned reference");
    }

    /** Verifies the shared contract is stable across deterministic JSON and YAML round trips. */
    @Test
    void roundTripsThroughStableCodec() {
        AgentDefinition agent = new AgentDefinition("assistant", "1.0.0", Map.of(), List.of(), List.of(),
                Map.of(), "model-a", List.of("tool-a@1"), null, List.of());
        ResolvedAgentReference resolved = factory.create("assistant@1.0.0", "assistant", "1.0.0", agent);
        FoundrySubstrateCodec codec = new FoundrySubstrateCodec();

        ResolvedAgentReference fromJson = codec.fromJson(codec.toJson(resolved), ResolvedAgentReference.class);
        ResolvedAgentReference fromYaml = codec.fromYaml(codec.toYaml(resolved), ResolvedAgentReference.class);

        assertThat(fromJson).isEqualTo(resolved);
        assertThat(fromYaml).isEqualTo(resolved);
        assertThat(codec.toJson(fromJson)).isEqualTo(codec.toJson(resolved));
        assertThat(codec.toYaml(fromYaml)).isEqualTo(codec.toYaml(resolved));
    }
}
