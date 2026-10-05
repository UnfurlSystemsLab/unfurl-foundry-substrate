package com.unfurl.foundry.substrate.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Neutral model-invocation request. Contains no provider-specific fields; adapters map
 * to and from concrete SDK types outside the substrate. {@code parameters} carries only the
 * provider-neutral option vocabulary {@link #OPTION_PARAMETERS}; phase data never travels here.
 */
public record ModelRequest(
        List<Message> messages,
        String modelRef,
        Map<String, Object> parameters,
        List<Map<String, Object>> toolSchemas,
        Map<String, Object> metadata
) {
/**
 * Constructs ModelRequest with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public ModelRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        toolSchemas = toolSchemas == null ? List.of() : List.copyOf(toolSchemas);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** Provider-neutral option vocabulary: the only keys a runtime sends as provider parameters and adapters accept. */
    public static final Set<String> OPTION_PARAMETERS = Set.of("temperature", "topP", "topK", "maxTokens", "stopSequences");

    /**
     * Option projector: selects the neutral provider options declared in a phase's input, in declaration order. Every other
     * entry is phase data for prompt templates or context projection and is deliberately excluded from provider parameters.
     */
    public static Map<String, Object> optionParameters(Map<String, Object> phaseInput) {
        var options = new LinkedHashMap<String, Object>();
        if (phaseInput != null) phaseInput.forEach((key, value) -> { if (OPTION_PARAMETERS.contains(key)) options.put(key, value); });
        return options;
    }
}
