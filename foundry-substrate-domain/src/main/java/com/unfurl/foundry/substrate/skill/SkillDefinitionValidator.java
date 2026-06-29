package com.unfurl.foundry.substrate.skill;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Structural validator for resolve-time skill definitions. */
/**
 * class for the Foundry AI substrate surface; documents the SkillDefinitionValidator contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public final class SkillDefinitionValidator {
    private static final Pattern SEMVER = Pattern.compile("^(0|[1-9]\\d*)(\\.(0|[1-9]\\d*)){2}(-[0-9A-Za-z.-]+)?(\\+[0-9A-Za-z.-]+)?$");

/**
 * Performs the validate operation for this component, translating validated inputs into the domain result expected by callers.
 */
    public void validate(SkillDefinition skill) {
        List<String> errors = new ArrayList<>();
        if (skill == null) {
            throw new IllegalArgumentException("Invalid skill definition: skill is required");
        }
        if (skill.name() == null || skill.name().isBlank()) {
            errors.add("name is required");
        }
        if (skill.version() == null || skill.version().isBlank()) {
            errors.add("version is required");
        } else if (!SEMVER.matcher(skill.version()).matches()) {
            errors.add("version must be SemVer");
        }
        Set<String> toolRefs = new HashSet<>();
        for (String toolRef : skill.toolRefs()) {
            if (toolRef == null || toolRef.isBlank()) {
                errors.add("toolRefs entries must be non-blank");
            } else if (!toolRefs.add(toolRef)) {
                errors.add("duplicate toolRef: " + toolRef);
            }
        }
        for (String permission : skill.permissionScope()) {
            if (permission == null || permission.isBlank()) {
                errors.add("permissionScope entries must be non-blank");
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid skill definition: " + String.join("; ", errors));
        }
    }
}
