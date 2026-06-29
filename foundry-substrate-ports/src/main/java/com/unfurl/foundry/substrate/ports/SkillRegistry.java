package com.unfurl.foundry.substrate.ports;

import com.unfurl.foundry.substrate.skill.SkillDefinition;
import com.unfurl.substrate.policy.ExecutionContext;

import java.util.Optional;

/**
 * interface for the Foundry AI substrate surface; documents the SkillRegistry contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public interface SkillRegistry {
    boolean hasSkill(String name, String version, ExecutionContext context);

    Optional<SkillDefinition> resolve(String name, String version, ExecutionContext context);
}
