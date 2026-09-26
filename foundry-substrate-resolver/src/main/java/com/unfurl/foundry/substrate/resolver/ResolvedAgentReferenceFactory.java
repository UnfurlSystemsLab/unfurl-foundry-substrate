package com.unfurl.foundry.substrate.resolver;

import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.agent.AgentHarnessLoopPolicy;
import com.unfurl.foundry.substrate.agent.AgentPhase;
import com.unfurl.foundry.substrate.serialization.CanonicalAgentDefinitionDigest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Factory: validates a pinned agentRef and projects its immutable invocation/audit contract. */
public final class ResolvedAgentReferenceFactory {
    private final CanonicalAgentDefinitionDigest digestService;

    /** Creates the Factory with the standard canonical digest Strategy. */
    public ResolvedAgentReferenceFactory() {
        this(new CanonicalAgentDefinitionDigest());
    }

    /** Creates the Factory with an injected digest Strategy. */
    public ResolvedAgentReferenceFactory(CanonicalAgentDefinitionDigest digestService) {
        this.digestService = digestService == null ? new CanonicalAgentDefinitionDigest() : digestService;
    }

    /** Projects a definition only when its identity exactly matches the pinned id and version. */
    public ResolvedAgentReference create(String reference, String expectedId, String expectedVersion, AgentDefinition agent) {
        if (agent == null) {
            throw new IllegalArgumentException("agent definition is required");
        }
        if (!agent.id().equals(expectedId) || !agent.version().equals(expectedVersion)) {
            throw new IllegalArgumentException("Resolved agent identity " + agent.id() + "@" + agent.version()
                    + " does not match pinned reference " + expectedId + "@" + expectedVersion);
        }
        return new ResolvedAgentReference(
                reference,
                agent.id(),
                agent.version(),
                digestService.digest(agent),
                stringMetadata(agent.metadata(), "inputSchemaRef", "input_schema_ref",
                        "inline:agent:" + reference + "#input"),
                stringMetadata(agent.metadata(), "terminalOutputSchemaRef", "terminal_output_schema_ref",
                        "inline:agent:" + reference + "#terminal-output"),
                harnessPolicy(agent.metadata()),
                dependencyProvenance(agent),
                agent.budgetPolicy(),
                stringList(first(agent.metadata(), "permissionConstraints", "permissionScope", "permissions")),
                map(first(agent.metadata(), "governanceConstraints", "governance")),
                map(first(agent.metadata(), "dcpBindingIdentity", "dcpBinding")),
                Map.of("digestAlgorithm", "SHA-256"));
    }

    /** Projection helper: derives sorted dependency provenance from graph and metadata references. */
    private Map<String, List<String>> dependencyProvenance(AgentDefinition agent) {
        Map<String, TreeSet<String>> values = new LinkedHashMap<>();
        addAll(values, "tools", agent.toolRefs());
        addAll(values, "skills", agent.skillRefs());
        add(values, "models", agent.defaultModelRef());
        addAll(values, "models", stringList(agent.metadata().get("modelRefs")));
        addAll(values, "prompts", stringList(agent.metadata().get("promptTemplateRefs")));
        addAll(values, "rag", stringList(first(agent.metadata(), "ragSourceRefs", "ragRefs")));
        for (AgentPhase phase : agent.phases()) {
            addAll(values, "tools", phase.allowedToolRefs());
            addAll(values, "skills", phase.skillRefs());
            add(values, "models", phase.modelRef());
            add(values, "prompts", phase.promptTemplateRef());
            add(values, "rag", phase.ragQueryRef());
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        values.forEach((kind, refs) -> result.put(kind, List.copyOf(refs)));
        return Map.copyOf(result);
    }

    /** Projection helper: reads a bounded harness policy from agent metadata aliases. */
    private AgentHarnessLoopPolicy harnessPolicy(Map<String, Object> metadata) {
        Object value = first(metadata, "harnessPolicy", "harness_policy");
        if (!(value instanceof Map<?, ?> policy)) {
            return AgentHarnessLoopPolicy.singleTurn();
        }
        int maxTurns = integer(first(policy, "maxTurns", "max_turns", "maxTurnsDefault", "max_turns_default"), 1);
        long maxDuration = longValue(first(policy, "maxDurationMillis", "max_duration_millis"), 0L);
        return new AgentHarnessLoopPolicy(maxTurns, maxDuration, Map.of());
    }

    /** Collection helper: adds non-blank references to a sorted dependency group. */
    private void addAll(Map<String, TreeSet<String>> values, String kind, Collection<String> refs) {
        if (refs != null) {
            refs.forEach(ref -> add(values, kind, ref));
        }
    }

    /** Collection helper: adds one non-blank dependency reference. */
    private void add(Map<String, TreeSet<String>> values, String kind, String ref) {
        if (ref != null && !ref.isBlank()) {
            values.computeIfAbsent(kind, ignored -> new TreeSet<>()).add(ref);
        }
    }

    /** Metadata selector: returns the first present alias from a map. */
    private Object first(Map<?, ?> values, String... keys) {
        if (values != null) {
            for (String key : keys) {
                if (values.containsKey(key)) {
                    return values.get(key);
                }
            }
        }
        return null;
    }

    /** Metadata selector: returns a non-blank string or a stable default. */
    private String stringMetadata(Map<String, Object> metadata, String firstKey, String secondKey, String fallback) {
        Object value = first(metadata, firstKey, secondKey);
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    /** Collection adapter: returns sorted distinct non-blank strings. */
    private List<String> stringList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        TreeSet<String> result = new TreeSet<>();
        collection.forEach(item -> {
            if (item != null && !String.valueOf(item).isBlank()) {
                result.add(String.valueOf(item));
            }
        });
        return List.copyOf(result);
    }

    /** Map adapter: converts arbitrary keys to strings for stable serialization. */
    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return Map.copyOf(result);
    }

    /** Numeric adapter: parses an integer policy value or uses its default. */
    private int integer(Object value, int fallback) {
        return value instanceof Number number ? number.intValue()
                : value == null ? fallback : Integer.parseInt(String.valueOf(value));
    }

    /** Numeric adapter: parses a long policy value or uses its default. */
    private long longValue(Object value, long fallback) {
        return value instanceof Number number ? number.longValue()
                : value == null ? fallback : Long.parseLong(String.valueOf(value));
    }
}
