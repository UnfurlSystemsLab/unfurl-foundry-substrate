package com.unfurl.foundry.substrate.resolver;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataReferenceResolverTest {
    private final DataReferenceResolver resolver = new DataReferenceResolver();

    @Test
    void omitsUnresolvedTopLevelReferences() {
        Map<String, Object> resolved = resolver.resolveInput(
                Map.of("present", "$.agent.input.name", "missing", "$.agent.input.absent"),
                Map.of("name", "Ada"),
                Map.of());

        assertThat(resolved).containsEntry("present", "Ada");
        assertThat(resolved).doesNotContainKey("missing");
    }

    @Test
    void resolvesNestedPhaseAndAgentReferences() {
        Map<String, Object> resolved = resolver.resolveInput(
                Map.of("payload", Map.of(
                        "name", "$.agent.input.user.name",
                        "summary", "$.phases.plan.output.summary",
                        "missing", "$.phases.plan.output.absent"),
                        "items", List.of("$.agent.input.user.name", "$.agent.input.absent")),
                Map.of("user", Map.of("name", "Ada")),
                Map.of("plan", Map.of("summary", "ready")));

        assertThat(resolved).containsEntry("payload", Map.of("name", "Ada", "summary", "ready"));
        assertThat(resolved).containsEntry("items", List.of("Ada"));
    }
}
