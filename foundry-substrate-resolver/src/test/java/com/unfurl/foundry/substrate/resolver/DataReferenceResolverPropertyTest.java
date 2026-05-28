package com.unfurl.foundry.substrate.resolver;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataReferenceResolverPropertyTest {
    private final DataReferenceResolver resolver = new DataReferenceResolver();

    @Property
    void referenceResolutionIsDeterministic(@ForAll("values") String value) {
        Map<String, Object> agentInput = Map.of("root", Map.of("leaf", value));
        Map<String, Map<String, Object>> phaseOutputs = Map.of("phase", Map.of("content", value.toUpperCase()));
        Map<String, Object> input = Map.of(
                "fromAgent", "$.agent.input.root.leaf",
                "fromPhase", "$.phases.phase.output.content",
                "literal", value);

        Map<String, Object> first = resolver.resolveInput(input, agentInput, phaseOutputs);
        Map<String, Object> second = resolver.resolveInput(input, agentInput, phaseOutputs);

        assertThat(second).isEqualTo(first);
    }

    @Provide
    Arbitrary<String> values() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
    }
}
