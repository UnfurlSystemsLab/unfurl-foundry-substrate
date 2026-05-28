package com.unfurl.foundry.substrate.agent;

import com.unfurl.substrate.domain.EdgeDefinition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentDefinitionValidatorTest {

    private final AgentDefinitionValidator validator = new AgentDefinitionValidator();

    @Test
    void rejectsCyclesIntroducedByConditionalEdges() {
        AgentDefinition agent = new AgentDefinition(
                "agent",
                "1",
                Map.of(),
                List.of(phase("a"), phase("b")),
                List.of(new EdgeDefinition("a", "b", null), new EdgeDefinition("b", "a", null)),
                Map.of(),
                "model",
                List.of()
        );

        assertThatThrownBy(() -> validator.validate(agent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dependency/edge cycle");
    }

    @Test
    void defaultsMissingBudgetPolicy() {
        AgentDefinition agent = new AgentDefinition(
                "agent",
                "1",
                Map.of(),
                List.of(phase("a")),
                List.of(),
                Map.of(),
                "model",
                List.of()
        );

        assertThat(agent.budgetPolicy()).isEqualTo(BudgetPolicy.none());
    }

    @Test
    void carriesExplicitBudgetPolicy() {
        BudgetPolicy policy = new BudgetPolicy(new BigDecimal("5.00"), new BigDecimal("10.00"),
                100L, 200L, 300L, Map.of());
        AgentDefinition agent = new AgentDefinition(
                "agent",
                "1",
                Map.of(),
                List.of(phase("a")),
                List.of(),
                Map.of(),
                "model",
                List.of(),
                policy
        );

        assertThat(agent.budgetPolicy()).isEqualTo(policy);
    }

    @Test
    void rejectsPhaseToolsOutsideAgentToolRefs() {
        AgentDefinition agent = new AgentDefinition(
                "agent",
                "1",
                Map.of(),
                List.of(new AgentPhase("a", null, null, List.of("missing"), null, Map.of(), Map.of(), List.of(), 0)),
                List.of(),
                Map.of(),
                "model",
                List.of("known")
        );

        assertThatThrownBy(() -> validator.validate(agent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allows unknown tool 'missing'");
    }

    @Test
    void rejectsModelAndPromptRefsWhenDefinitionDeclaresCatalogs() {
        AgentDefinition agent = new AgentDefinition(
                "agent",
                "1",
                Map.of("modelRefs", List.of("model"), "promptTemplateRefs", List.of("prompt")),
                List.of(new AgentPhase("a", "missing-prompt", "missing-model", List.of(), null, Map.of(), Map.of(), List.of(), 0)),
                List.of(),
                Map.of(),
                "model",
                List.of()
        );

        assertThatThrownBy(() -> validator.validate(agent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown model 'missing-model'")
                .hasMessageContaining("unknown prompt template 'missing-prompt'");
    }

    private AgentPhase phase(String id) {
        return new AgentPhase(id, null, null, List.of(), null, Map.of("prompt", id), Map.of(), List.of(), 0);
    }
}
