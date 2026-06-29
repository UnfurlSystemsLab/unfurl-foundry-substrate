package com.unfurl.foundry.substrate.prompt;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * record for the Foundry AI substrate surface; documents the PromptTemplate contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public record PromptTemplate(
        @NotBlank String id,
        @NotBlank String version,
        List<PromptSegment> segments,
        List<String> variables
) {
/**
 * Constructs PromptTemplate with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public PromptTemplate {
        segments = segments == null ? List.of() : List.copyOf(segments);
        variables = variables == null ? List.of() : List.copyOf(variables);
    }
}
