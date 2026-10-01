package com.unfurl.foundry.substrate.ports;

import java.util.Map;

/** Value Object: engine-owned tool-attempt identity, separate from provider correlation and caller metadata. */
public record ToolCallScope(String tenantId, String agentRunId, String phaseId, String toolCallId) {
    /** Constructor: requires execution identity; tenantless embedded runs remain tenantless, never defaulted. */
    public ToolCallScope {
        if (tenantId != null) require(tenantId, "tenantId");
        require(agentRunId, "agentRunId");
        require(phaseId, "phaseId");
        require(toolCallId, "toolCallId");
    }

    /** Projector: exposes only reserved engine identity fields, never authority or payload. */
    public Map<String, Object> metadata() {
        var values = new java.util.LinkedHashMap<String, Object>();
        if (tenantId != null) values.put("tenantId", tenantId);
        values.put("agentRunId", agentRunId);
        values.put("phaseId", phaseId);
        values.put("toolCallId", toolCallId);
        return Map.copyOf(values);
    }

    /** Factory: rejects absent/coerced scope rather than accepting a provider or caller fallback. */
    public static ToolCallScope from(Map<String, Object> metadata) {
        return new ToolCallScope(metadata.containsKey("tenantId") ? text(metadata, "tenantId") : null, text(metadata, "agentRunId"),
                text(metadata, "phaseId"), text(metadata, "toolCallId"));
    }

    /** Invariant guard: policy normalization may add metadata but cannot replace engine scope. */
    public void verify(Map<String, Object> metadata) {
        if (!equals(from(metadata))) throw new IllegalStateException("tool call scope changed");
    }

    /** Typed parser: identity fields must remain explicit strings. */
    private static String text(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (!(value instanceof String text)) throw new IllegalArgumentException(key + " is required");
        return text;
    }

    /** Validation Strategy: rejects incomplete scope before any policy or physical call. */
    private static void require(String value, String key) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " is required");
    }
}
