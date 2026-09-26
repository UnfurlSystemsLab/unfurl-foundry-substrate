package com.unfurl.foundry.substrate.serialization;

import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.agent.AgentPhase;
import org.junit.jupiter.api.Test;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies stable definition identity while excluding mutable raw secret/runtime metadata. */
class CanonicalAgentDefinitionDigestTest {
    private final CanonicalAgentDefinitionDigest digest = new CanonicalAgentDefinitionDigest();

    /** Equivalent map insertion order and changed raw secrets produce the same digest. */
    @Test
    void equivalentDefinitionsHaveSameDigest() {
        Map<String, Object> firstMetadata = new LinkedHashMap<>();
        firstMetadata.put("owner", "platform");
        firstMetadata.put("apiKey", "first-value");
        Map<String, Object> secondMetadata = new LinkedHashMap<>();
        secondMetadata.put("apiKey", "second-value");
        secondMetadata.put("owner", "platform");

        assertThat(digest.digest(agent(firstMetadata, "model-a")))
                .isEqualTo(digest.digest(agent(secondMetadata, "model-a")));
    }

    /** Any executable semantic change remains digest-bearing. */
    @Test
    void semanticDefinitionChangeChangesDigest() {
        assertThat(digest.digest(agent(Map.of("owner", "platform"), "model-a")))
                .isNotEqualTo(digest.digest(agent(Map.of("owner", "platform"), "model-b")));
    }

    /** Property: changing a generated executable model reference always changes definition identity. */
    @Property(tries = 25)
    void generatedSemanticChangesAlterDigest(@ForAll @IntRange(min = 1, max = 1000) int suffix) {
        assertThat(digest.digest(agent(Map.of(), "model-" + suffix)))
                .isNotEqualTo(digest.digest(agent(Map.of(), "model-" + (suffix + 1))));
    }

    /** Fixture Factory: creates a minimal agent with one digest-bearing phase model reference. */
    private AgentDefinition agent(Map<String, Object> metadata, String modelRef) {
        return new AgentDefinition("assistant", "1.0.0", metadata,
                List.of(new AgentPhase("answer", null, modelRef, List.of(), null,
                        Map.of(), Map.of(), List.of(), 0)),
                List.of(), Map.of("type", "object"), modelRef, List.of());
    }
}
