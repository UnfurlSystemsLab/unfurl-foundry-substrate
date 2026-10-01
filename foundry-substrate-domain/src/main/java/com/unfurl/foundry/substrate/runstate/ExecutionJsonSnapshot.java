package com.unfurl.foundry.substrate.runstate;

import java.util.*;

/** Snapshot Builder: defensively copies bounded finite JSON execution payloads without serialization dependencies. */
public final class ExecutionJsonSnapshot {
    /** Utility constructor: all snapshots are created through the bounded builder. */
    private ExecutionJsonSnapshot() { }

    /** Factory: freezes a JSON object including nested nulls; rejects cycles via the depth bound. */
    public static Map<String, Object> freeze(Map<?, ?> source) {
        return object(source == null ? Map.of() : source, 0, new int[]{100000});
    }

    /** Recursive Builder: admits string-keyed JSON objects and freezes insertion order. */
    private static Map<String, Object> object(Map<?, ?> source, int depth, int[] remaining) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!(key instanceof String name)) throw new IllegalArgumentException("execution JSON keys must be strings");
            result.put(name, value(value, depth + 1, remaining));
        });
        return Collections.unmodifiableMap(result);
    }

    /** Recursive Builder: keeps immutable scalars, copies collections and rejects opaque/non-finite values. */
    private static Object value(Object value, int depth, int[] remaining) {
        if (depth > 64 || --remaining[0] < 0) throw new IllegalArgumentException("execution JSON exceeds snapshot bounds");
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Integer
                || value instanceof Long || value instanceof Short || value instanceof Byte
                || value instanceof java.math.BigInteger || value instanceof java.math.BigDecimal) return value;
        if (value instanceof Double number && Double.isFinite(number)) return value;
        if (value instanceof Float number && Float.isFinite(number)) return value;
        if (value instanceof Map<?, ?> map) return object(map, depth, remaining);
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            list.forEach(item -> result.add(value(item, depth + 1, remaining)));
            return Collections.unmodifiableList(result);
        }
        throw new IllegalArgumentException("execution snapshot requires finite JSON values");
    }
}
