package com.unfurl.foundry.substrate.resolver;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves {@code $.agent.input.<path>} and {@code $.phases.<id>.output.<path>} references
 * against the agent input and completed phase outputs. The AI analogue of the substrate's
 * {@code DataResolver} — and, unlike the substrate engine's current state, it is wired into
 * the embedded runtime from day one.
 */
public final class DataReferenceResolver {

    public boolean isReference(Object value) {
        return value instanceof String s && s.startsWith("$.");
    }

    /** Resolve a single reference expression; empty when the path is absent. */
    public Optional<Object> resolve(
            String expression,
            Map<String, Object> agentInput,
            Map<String, Map<String, Object>> phaseOutputs
    ) {
        if (expression == null || !expression.startsWith("$.")) {
            return Optional.empty();
        }
        String[] parts = expression.substring(2).split("\\.");
        if (parts.length >= 2 && parts[0].equals("agent") && parts[1].equals("input")) {
            return walk(agentInput, List.of(parts).subList(2, parts.length));
        }
        if (parts.length >= 3 && parts[0].equals("phases") && parts[2].equals("output")) {
            Map<String, Object> output = phaseOutputs.get(parts[1]);
            if (output == null) {
                return Optional.empty();
            }
            return walk(output, List.of(parts).subList(3, parts.length));
        }
        return Optional.empty();
    }

    /**
     * Resolve every reference-valued entry in a phase input map, leaving literals intact.
     * Unresolved references become {@code null}.
     */
    public Map<String, Object> resolveInput(
            Map<String, Object> input,
            Map<String, Object> agentInput,
            Map<String, Map<String, Object>> phaseOutputs
    ) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            if (isReference(value)) {
                resolved.put(key, resolve((String) value, agentInput, phaseOutputs).orElse(null));
            } else {
                resolved.put(key, value);
            }
        });
        return resolved;
    }

    private Optional<Object> walk(Map<String, Object> root, List<String> path) {
        Object value = root;
        for (String segment : path) {
            if (!(value instanceof Map<?, ?> map) || !map.containsKey(segment)) {
                return Optional.empty();
            }
            value = map.get(segment);
        }
        return Optional.ofNullable(value);
    }
}
