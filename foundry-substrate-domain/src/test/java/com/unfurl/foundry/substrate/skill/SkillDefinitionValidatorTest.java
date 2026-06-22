package com.unfurl.foundry.substrate.skill;

import com.unfurl.foundry.substrate.agent.BudgetPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillDefinitionValidatorTest {
    private final SkillDefinitionValidator validator = new SkillDefinitionValidator();

    @Test
    void acceptsValidSkill() {
        assertThatNoException().isThrownBy(() -> validator.validate(skill("research", "1.0.0", List.of("search"))));
    }

    @Test
    void rejectsBlankNameAndVersion() {
        assertThatThrownBy(() -> validator.validate(skill(" ", " ", List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name is required")
                .hasMessageContaining("version is required");
    }

    @Test
    void rejectsNonSemverVersion() {
        assertThatThrownBy(() -> validator.validate(skill("research", "1", List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version must be SemVer");
    }

    @Test
    void rejectsDuplicateToolRefs() {
        assertThatThrownBy(() -> validator.validate(skill("research", "1.0.0", List.of("search", "search"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate toolRef: search");
    }

    @Test
    void rejectsBlankPermissionScopeEntries() {
        SkillDefinition skill = new SkillDefinition("research", "1.0.0", null, List.of(), null, List.of(),
                null, BudgetPolicy.none(), List.of("tools.search", " "), Map.of(), Map.of(), Map.of());

        assertThatThrownBy(() -> validator.validate(skill))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("permissionScope entries must be non-blank");
    }

    private SkillDefinition skill(String name, String version, List<String> toolRefs) {
        return new SkillDefinition(name, version, "Research", toolRefs, "prompt", List.of("docs"),
                "model", BudgetPolicy.none(), List.of("tools.search"), Map.of(), Map.of(), Map.of());
    }
}
