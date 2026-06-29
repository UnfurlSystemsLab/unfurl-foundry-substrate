package com.unfurl.foundry.substrate.resolver;

import java.util.LinkedHashMap;
import java.util.ArrayList;
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

/**
 * Performs the isReference operation for this component, translating validated inputs into the domain result expected by callers.
 */
    public boolean isReference(Object value) {
        return value instanceof String s && s.startsWith("$.");
    }

    /** Resolve a single reference expression; empty when the path is absent. */
/**
 * Performs the resolve operation for this component, translating validated inputs into the domain result expected by callers.
 */
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
     * Unresolved references are omitted so downstream immutable maps never receive null
     * values from a missing reference.
     */
    public Map<String, Object> resolveInput(
            Map<String, Object> input,
            Map<String, Object> agentInput,
            Map<String, Map<String, Object>> phaseOutputs
    ) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            if (isReference(value)) {
                resolve((String) value, agentInput, phaseOutputs).ifPresent(resolvedValue -> resolved.put(key, resolvedValue));
            } else {
                resolved.put(key, resolveNested(value, agentInput, phaseOutputs));
            }
        });
        return resolved;
    }

/**
 * Performs the resolveNested operation for this component, translating validated inputs into the domain result expected by callers.
 */
    private Object resolveNested(
            Object value,
            Map<String, Object> agentInput,
            Map<String, Map<String, Object>> phaseOutputs
    ) {
        if (isReference(value)) {
            return resolve((String) value, agentInput, phaseOutputs).orElse(null);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> resolved = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> {
                Object next = resolveNested(nestedValue, agentInput, phaseOutputs);
                if (next != null) {
                    resolved.put(String.valueOf(key), next);
                }
            });
            return Map.copyOf(resolved);
        }
        if (value instanceof List<?> list) {
            List<Object> resolved = new ArrayList<>();
            for (Object item : list) {
                Object next = resolveNested(item, agentInput, phaseOutputs);
                if (next != null) {
                    resolved.add(next);
                }
            }
            return List.copyOf(resolved);
        }
        return value;
    }

/**
 * Implements the walk helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
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
