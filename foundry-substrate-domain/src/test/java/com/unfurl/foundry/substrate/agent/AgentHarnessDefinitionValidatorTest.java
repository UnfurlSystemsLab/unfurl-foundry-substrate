package com.unfurl.foundry.substrate.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentHarnessDefinitionValidatorTest {
    private final AgentHarnessDefinitionValidator validator = new AgentHarnessDefinitionValidator();

    @Test
    void rejectsInvalidWrappedAgent() {
        AgentDefinition invalidAgent = new AgentDefinition("agent", "1", Map.of(), List.of(),
                List.of(), Map.of(), null, List.of());
        AgentHarnessDefinition harness = new AgentHarnessDefinition("harness", "1", Map.of(),
                invalidAgent, AgentHarnessLoopPolicy.singleTurn());

        assertThatThrownBy(() -> validator.validate(harness))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must declare at least one phase");
    }

    @Test
    void rejectsInvalidHarnessId() {
        AgentHarnessDefinition harness = new AgentHarnessDefinition("bad id", "1", Map.of(),
                validAgent(), AgentHarnessLoopPolicy.singleTurn());

        assertThatThrownBy(() -> validator.validate(harness))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid harness id");
    }

    /**
     * Fixture builder: creates the smallest valid static-DAG agent accepted by
     * the substrate agent validator.
     */
    private AgentDefinition validAgent() {
        return new AgentDefinition("agent", "1", Map.of(),
                List.of(new AgentPhase("turn", null, null, List.of(), null,
                        Map.of(), Map.of(), List.of(), 0)),
                List.of(), Map.of(), null, List.of());
    }
}
