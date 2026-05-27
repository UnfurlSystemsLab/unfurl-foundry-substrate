package com.unfurl.foundry.substrate.prompt;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record PromptTemplate(
        @NotBlank String id,
        @NotBlank String version,
        List<PromptSegment> segments,
        List<String> variables
) {
    public PromptTemplate {
        segments = segments == null ? List.of() : List.copyOf(segments);
        variables = variables == null ? List.of() : List.copyOf(variables);
    }
}
