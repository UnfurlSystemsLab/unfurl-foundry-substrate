package com.unfurl.foundry.substrate.agent;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * One bounded reasoning step of an agent: assemble a prompt, optionally retrieve,
 * call the model, optionally call tools, produce structured output.
 *
 * <p>{@code maxToolIterations} bounds the tool-call loop within a phase. It is a
 * control-flow primitive, not a capability name.
 */
public record AgentPhase(
        @NotBlank String id,
        String promptTemplateRef,
        String modelRef,
        List<String> allowedToolRefs,
        String ragQueryRef,
        Map<String, Object> input,
        Map<String, Object> outputMapping,
        List<String> dependencies,
        int maxToolIterations,
        List<String> skillRefs
) {
    public AgentPhase(
            String id,
            String promptTemplateRef,
            String modelRef,
            List<String> allowedToolRefs,
            String ragQueryRef,
            Map<String, Object> input,
            Map<String, Object> outputMapping,
            List<String> dependencies,
            int maxToolIterations
    ) {
        this(id, promptTemplateRef, modelRef, allowedToolRefs, ragQueryRef, input, outputMapping, dependencies,
                maxToolIterations, List.of());
    }

    public AgentPhase {
        allowedToolRefs = allowedToolRefs == null ? List.of() : List.copyOf(allowedToolRefs);
        input = input == null ? Map.of() : Map.copyOf(input);
        outputMapping = outputMapping == null ? Map.of() : Map.copyOf(outputMapping);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        skillRefs = skillRefs == null ? List.of() : List.copyOf(skillRefs);
        if (maxToolIterations < 0) {
            throw new IllegalArgumentException("maxToolIterations must be >= 0");
        }
    }
}
