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
        List<String> skillRefs,
        String outputSchemaRef,
        String semanticValidatorRef,
        CorrectionPolicy correctionPolicy
) {
/**
 * Constructs AgentPhase with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
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
                maxToolIterations, List.of(), null, null, CorrectionPolicy.none());
    }

    /** Compatibility constructor: preserves the pre-validation-slice skill-aware phase shape. */
    public AgentPhase(
            String id,
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
        this(id, promptTemplateRef, modelRef, allowedToolRefs, ragQueryRef, input, outputMapping,
                dependencies, maxToolIterations, skillRefs, null, null, CorrectionPolicy.none());
    }

/**
 * Constructs AgentPhase with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public AgentPhase {
        allowedToolRefs = allowedToolRefs == null ? List.of() : List.copyOf(allowedToolRefs);
        input = input == null ? Map.of() : Map.copyOf(input);
        outputMapping = outputMapping == null ? Map.of() : Map.copyOf(outputMapping);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        skillRefs = skillRefs == null ? List.of() : List.copyOf(skillRefs);
        correctionPolicy = correctionPolicy == null ? CorrectionPolicy.none() : correctionPolicy;
        if (maxToolIterations < 0) {
            throw new IllegalArgumentException("maxToolIterations must be >= 0");
        }
    }
}
