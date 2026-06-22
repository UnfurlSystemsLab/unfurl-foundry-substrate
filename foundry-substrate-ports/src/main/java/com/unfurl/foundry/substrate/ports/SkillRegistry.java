package com.unfurl.foundry.substrate.ports;

import com.unfurl.foundry.substrate.skill.SkillDefinition;
import com.unfurl.substrate.policy.ExecutionContext;

import java.util.Optional;

public interface SkillRegistry {
    boolean hasSkill(String name, String version, ExecutionContext context);

    Optional<SkillDefinition> resolve(String name, String version, ExecutionContext context);
}
