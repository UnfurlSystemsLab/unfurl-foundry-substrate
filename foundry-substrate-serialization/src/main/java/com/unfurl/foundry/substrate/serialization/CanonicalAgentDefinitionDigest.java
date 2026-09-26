package com.unfurl.foundry.substrate.serialization;

import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.agent.BudgetPolicy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Strategy: computes a reproducible SHA-256 identity over executable agent-definition semantics. */
public final class CanonicalAgentDefinitionDigest {
    private static final Set<String> EXCLUDED_METADATA_KEYS = Set.of(
            "apikey", "accesstoken", "password", "clientsecret", "secret", "secrets", "credentials",
            "runtimestate", "deploymentstate", "lastupdated", "updatedat");

    private final FoundrySubstrateCodec codec;

    /** Creates the digest Strategy with the stable public substrate codec. */
    public CanonicalAgentDefinitionDigest() {
        this(new FoundrySubstrateCodec());
    }

    /** Creates the digest Strategy with an injected codec for deterministic testing and reuse. */
    public CanonicalAgentDefinitionDigest(FoundrySubstrateCodec codec) {
        this.codec = codec == null ? new FoundrySubstrateCodec() : codec;
    }

    /** Returns the lowercase hexadecimal SHA-256 digest of the canonical definition JSON. */
    public String digest(AgentDefinition definition) {
        byte[] bytes = canonicalJson(definition).getBytes(StandardCharsets.UTF_8);
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** Returns stable JSON after excluding raw mutable secret/runtime metadata only. */
    public String canonicalJson(AgentDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("agent definition is required");
        }
        AgentDefinition canonical = new AgentDefinition(
                definition.id(), definition.version(), sanitizeMap(definition.metadata()), definition.phases(),
                definition.edges(), definition.inputSchema(), definition.defaultModelRef(), definition.toolRefs(),
                sanitizeBudgetPolicy(definition.budgetPolicy()), definition.skillRefs());
        return codec.toJson(canonical);
    }

    /** Copies semantic budget ceilings while removing mutable secret/runtime budget metadata. */
    private BudgetPolicy sanitizeBudgetPolicy(BudgetPolicy policy) {
        if (policy == null) {
            return null;
        }
        return new BudgetPolicy(
                policy.defaultBudgetUsd(),
                policy.maxBudgetUsd(),
                policy.maxPromptTokens(),
                policy.maxCompletionTokens(),
                policy.maxTotalTokens(),
                sanitizeMap(policy.metadata()));
    }

    /** Recursive Filter: removes excluded mutable keys while retaining semantic nested metadata. */
    private Map<String, Object> sanitizeMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!EXCLUDED_METADATA_KEYS.contains(normalizedKey(key))) {
                result.put(key, sanitizeValue(value));
            }
        });
        return Collections.unmodifiableMap(result);
    }

    /** Recursive adapter: sanitizes nested maps and lists without changing scalar semantics. */
    private Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                String textKey = String.valueOf(key);
                if (!EXCLUDED_METADATA_KEYS.contains(normalizedKey(textKey))) {
                    normalized.put(textKey, sanitizeValue(item));
                }
            });
            return Collections.unmodifiableMap(normalized);
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            list.forEach(item -> normalized.add(sanitizeValue(item)));
            return Collections.unmodifiableList(normalized);
        }
        return value;
    }

    /** Key normalizer: makes exclusion matching independent of common JSON/YAML key styles. */
    private String normalizedKey(String key) {
        return key == null ? "" : key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }
}
