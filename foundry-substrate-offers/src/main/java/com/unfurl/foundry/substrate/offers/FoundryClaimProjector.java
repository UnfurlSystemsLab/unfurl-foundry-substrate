package com.unfurl.foundry.substrate.offers;

import com.unfurl.dcp.claim.Claim;
import com.unfurl.dcp.claim.ClaimMetadata;
import com.unfurl.dcp.claim.ComponentKind;
import com.unfurl.dcp.claim.Dependencies;
import com.unfurl.dcp.claim.Identity;
import com.unfurl.dcp.claim.IntegrationPorts;
import com.unfurl.dcp.claim.Offer;
import com.unfurl.dcp.projection.DcpProjectionProjector;
import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.agent.AgentPhase;
import com.unfurl.foundry.substrate.skill.SkillDefinition;
import com.unfurl.foundry.substrate.tool.ToolDefinition;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Projects the foundry-substrate composition graph
 * (Foundry &rarr; Agent &rarr; Phase &rarr; {Tool, Prompt, RAG, Model, Skill}) into DCP
 * {@link Claim}s whose {@code metadata.extensions} carry containment via
 * {@link DcpProjectionProjector#EXT_CONTAINS}.
 *
 * <p>The foundry domain types are not DCP claims and carry no containment metadata; this projector
 * <em>synthesizes</em> that metadata from their existing string refs. Agent-wide refs remain under the
 * agent claim, while per-phase {@code promptTemplateRef}, {@code modelRef}, {@code allowedToolRefs},
 * {@code ragQueryRef}, and {@code skillRefs} are represented by explicit phase claims. The resulting
 * claim map can be fed directly to {@link DcpProjectionProjector}, so the recursive projection (and the
 * Studio semantic-zoom navigator that consumes it) cover foundry agents with no further UI work.
 *
 * <p>A claim is emitted for every referenced node so the projector never sees a dangling
 * {@code contains} URI. Capability {@link Offer}s reuse {@link AiOffers}, kept separate from containment.
 */
public final class FoundryClaimProjector {
    public static final String URN_PREFIX = "urn:unfurl:foundry:";
    public static final String LEVEL_FOUNDRY = "FOUNDRY";
    public static final String LEVEL_AGENT = "AGENT";
    public static final String LEVEL_PHASE = "PHASE";
    public static final String LEVEL_SKILL = "SKILL";
    public static final String LEVEL_TOOL = "TOOL";
    public static final String LEVEL_PROMPT = "PROMPT";
    public static final String LEVEL_MODEL = "MODEL";
    public static final String LEVEL_RAG = "RAG";

    /**
     * Project one agent's subtree into a claim map keyed by claim URI. The agent claim's URI is
     * {@link #agentUri(String)}; pass it as the projection root to {@link DcpProjectionProjector}.
     */
    public Map<URI, Claim> project(
            AgentDefinition agent,
            Map<String, SkillDefinition> skillsById,
            Map<String, ToolDefinition> toolsByName) {
        Map<URI, Claim> claims = new LinkedHashMap<>();
        addAgent(agent, skillsById, toolsByName, claims);
        return claims;
    }

    /**
     * Project a Foundry deployment: a {@code FOUNDRY} root claim that contains the given agents, plus
     * each agent's subtree. Use {@code foundryUri} as the projection root.
     */
    public Map<URI, Claim> projectFoundry(
            URI foundryUri,
            String foundryLabel,
            List<AgentDefinition> agents,
            Map<String, SkillDefinition> skillsById,
            Map<String, ToolDefinition> toolsByName) {
        Map<URI, Claim> claims = new LinkedHashMap<>();
        LinkedHashSet<URI> agentUris = new LinkedHashSet<>();
        for (AgentDefinition agent : agents) {
            agentUris.add(agentUri(agent.id()));
            addAgent(agent, skillsById, toolsByName, claims);
        }
        claims.put(foundryUri, claim(
                foundryUri,
                foundryLabel == null || foundryLabel.isBlank() ? "Foundry" : foundryLabel,
                LEVEL_FOUNDRY,
                ComponentKind.INFRASTRUCTURE,
                List.copyOf(agentUris),
                List.of()));
        return claims;
    }

    /**
     * Builder helper: emits one agent claim, explicit phase claims, and all transitive referenced
     * runtime claims while preserving non-dangling containment for the DCP projector.
     */
    private void addAgent(
            AgentDefinition agent,
            Map<String, SkillDefinition> skillsById,
            Map<String, ToolDefinition> toolsByName,
            Map<URI, Claim> claims) {
        URI uri = agentUri(agent.id());
        LinkedHashSet<URI> children = new LinkedHashSet<>();
        LinkedHashSet<URI> referencedRuntimeChildren = new LinkedHashSet<>();
        agent.toolRefs().forEach(ref -> children.add(toolUri(ref)));
        agent.skillRefs().forEach(ref -> children.add(skillUri(ref)));
        addIfPresent(children, agent.defaultModelRef(), this::modelUri);
        for (AgentPhase phase : agent.phases()) {
            URI phaseUri = phaseUri(agent.id(), phase.id());
            LinkedHashSet<URI> phaseChildren = new LinkedHashSet<>();
            addIfPresent(phaseChildren, phase.promptTemplateRef(), this::promptUri);
            addIfPresent(phaseChildren, phase.modelRef(), this::modelUri);
            addIfPresent(phaseChildren, phase.ragQueryRef(), this::ragUri);
            phase.allowedToolRefs().forEach(ref -> phaseChildren.add(toolUri(ref)));
            phase.skillRefs().forEach(ref -> phaseChildren.add(skillUri(ref)));
            claims.put(phaseUri, claim(phaseUri, agent.id() + "." + phase.id(), LEVEL_PHASE, ComponentKind.COMPONENT,
                    List.copyOf(phaseChildren), List.of()));
            children.add(phaseUri);
            referencedRuntimeChildren.addAll(phaseChildren);
        }
        claims.put(uri, claim(uri, agent.id(), LEVEL_AGENT, ComponentKind.INTELLIGENT_COMPONENT,
                List.copyOf(children), agentRunOffers(agent)));
        referencedRuntimeChildren.addAll(children);
        referencedRuntimeChildren.removeIf(claims::containsKey);
        ensureChildren(referencedRuntimeChildren, skillsById, toolsByName, claims);
    }

    /**
     * Projector helper: walks referenced child URIs, expands skills into their own DCP children, and
     * leaves tool/model/rag/prompt nodes terminal.
     */
    private void ensureChildren(
            LinkedHashSet<URI> seed,
            Map<String, SkillDefinition> skillsById,
            Map<String, ToolDefinition> toolsByName,
            Map<URI, Claim> claims) {
        Deque<URI> pending = new ArrayDeque<>(seed);
        while (!pending.isEmpty()) {
            URI uri = pending.poll();
            if (claims.containsKey(uri)) {
                continue;
            }
            String kind = kindOf(uri);
            String ref = refOf(uri);
            switch (kind) {
                case "skill" -> {
                    SkillDefinition skill = skillsById == null ? null : skillsById.get(ref);
                    LinkedHashSet<URI> skillChildren = new LinkedHashSet<>();
                    if (skill != null) {
                        skill.toolRefs().forEach(toolRef -> skillChildren.add(toolUri(toolRef)));
                        addIfPresent(skillChildren, skill.promptFragmentRef(), this::promptUri);
                        skill.ragSourceRefs().forEach(ragRef -> skillChildren.add(ragUri(ragRef)));
                        addIfPresent(skillChildren, skill.defaultModelRef(), this::modelUri);
                    }
                    claims.put(uri, claim(uri, ref, LEVEL_SKILL, ComponentKind.INTELLIGENT_COMPONENT,
                            List.copyOf(skillChildren), offersFor(AiOffers.SKILL_INVOKE)));
                    pending.addAll(skillChildren);
                }
                case "tool" -> {
                    ToolDefinition tool = toolsByName == null ? null : toolsByName.get(ref);
                    String label = tool != null && tool.name() != null && !tool.name().isBlank() ? tool.name() : ref;
                    claims.put(uri, claim(uri, label, LEVEL_TOOL, ComponentKind.COMPONENT,
                            List.of(), offersFor(AiOffers.TOOL_CALL)));
                }
                case "model" -> claims.put(uri, claim(uri, ref, LEVEL_MODEL, ComponentKind.COMPONENT,
                        List.of(), offersFor(AiOffers.PROVIDER_CALL)));
                case "rag" -> claims.put(uri, claim(uri, ref, LEVEL_RAG, ComponentKind.COMPONENT,
                        List.of(), offersFor(AiOffers.RAG_SEARCH)));
                default -> claims.put(uri, claim(uri, ref, LEVEL_PROMPT, ComponentKind.COMPONENT,
                        List.of(), List.of()));
            }
        }
    }

    /**
     * Builder helper: adds a non-blank string ref as a typed URI while preserving absent optional refs.
     */
    private static void addIfPresent(LinkedHashSet<URI> target, String ref, java.util.function.Function<String, URI> toUri) {
        if (ref != null && !ref.isBlank()) {
            target.add(toUri.apply(ref));
        }
    }

    /**
     * Factory method: creates a structural DCP claim and carries containment only in metadata
     * extensions, keeping capability offers separate from graph shape.
     */
    private Claim claim(URI uri, String label, String level, ComponentKind kind, List<URI> children, List<Offer> offers) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("level", level);
        extensions.put("dcpType", level);
        extensions.put(DcpProjectionProjector.EXT_CONTAINS,
                children.stream().map(URI::toString).sorted().toList());
        return new Claim(
                new Identity(uri, label, kind, "1.0.0", "Unfurl", URI.create("urn:unfurl")),
                null,
                List.of(),
                new Dependencies(List.of()),
                offers,
                null,
                null,
                new IntegrationPorts(Map.of()),
                AiOffers.faultPolicyFor(offers),
                new ClaimMetadata("0.2.0", "1.0.0", Instant.now(), extensions));
    }

    /**
     * Factory method: selects one canonical AI offer so generated claims align with the Foundry DCP
     * capability surface and fault vocabulary.
     */
    private static List<Offer> offersFor(String capability) {
        return AiOffers.standardAiOffers("1.0.0").stream()
                .filter(offer -> offer.capability().equals(capability))
                .toList();
    }

    /**
     * Factory method: creates the {@code agent.run} offer for one concrete agent, allowing
     * agent metadata to narrow execution modes while keeping the DCP capability name stable.
     */
    private static List<Offer> agentRunOffers(AgentDefinition agent) {
        Map<String, Object> metadata = agent.metadata();
        return List.of(AiOffers.agentRunOffer(
                "1.0.0",
                stringList(firstPresent(metadata, "execution_modes", "executionModes")),
                stringValue(firstPresent(metadata, "default_execution_mode", "defaultExecutionMode")),
                mapValue(firstPresent(metadata, "mode_policies", "modePolicies"))));
    }

    /**
     * Metadata reader: returns the first present value across snake_case and Java-style aliases.
     */
    private static Object firstPresent(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            if (metadata != null && metadata.containsKey(key)) {
                return metadata.get(key);
            }
        }
        return null;
    }

    /**
     * Metadata adapter: normalizes a scalar or collection metadata value into a string list.
     */
    private static List<String> stringList(Object value) {
        if (value instanceof Iterable<?> iterable) {
            List<String> values = new java.util.ArrayList<>();
            for (Object item : iterable) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    values.add(String.valueOf(item));
                }
            }
            return List.copyOf(values);
        }
        if (value instanceof String text && !text.isBlank()) {
            return List.of(text.split(",")).stream()
                    .map(String::trim)
                    .filter(mode -> !mode.isBlank())
                    .toList();
        }
        return List.of(AiOffers.MODE_SIMPLE);
    }

    /**
     * Metadata adapter: returns a string metadata value or an empty value when absent.
     */
    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * Metadata adapter: converts map-like metadata into the DCP details shape expected by AiOffers.
     */
    private static Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        map.forEach((key, nextValue) -> normalized.put(String.valueOf(key), normalizeValue(nextValue)));
        return Map.copyOf(normalized);
    }

    /**
     * Metadata adapter: recursively normalizes nested mode-policy maps while preserving scalar values.
     */
    private static Object normalizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return mapValue(map);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> values = new java.util.ArrayList<>();
            for (Object item : iterable) {
                values.add(normalizeValue(item));
            }
            return List.copyOf(values);
        }
        return value;
    }

    /**
     * Factory method: returns the stable DCP URI used to join Flow nodes to Foundry agents.
     */
    public URI agentUri(String id) {
        return URI.create(URN_PREFIX + "agent:" + id);
    }

    /**
     * Factory method: returns the stable DCP URI for a Foundry phase owned by an agent.
     */
    public URI phaseUri(String agentId, String phaseId) {
        return URI.create(URN_PREFIX + "phase:" + agentId + "." + phaseId);
    }

    /**
     * Factory method: returns the stable DCP URI for a governed skill reference.
     */
    public URI skillUri(String ref) {
        return URI.create(URN_PREFIX + "skill:" + ref);
    }

    /**
     * Factory method: returns the stable DCP URI for a tool reference.
     */
    public URI toolUri(String ref) {
        return URI.create(URN_PREFIX + "tool:" + ref);
    }

    /**
     * Factory method: returns the stable DCP URI for a prompt template or fragment reference.
     */
    public URI promptUri(String ref) {
        return URI.create(URN_PREFIX + "prompt:" + ref);
    }

    /**
     * Factory method: returns the stable DCP URI for a model provider/config reference.
     */
    public URI modelUri(String ref) {
        return URI.create(URN_PREFIX + "model:" + ref);
    }

    /**
     * Factory method: returns the stable DCP URI for a RAG query/source reference.
     */
    public URI ragUri(String ref) {
        return URI.create(URN_PREFIX + "rag:" + ref);
    }

    /**
     * Parser helper: extracts the URI kind segment used by {@link #ensureChildren}.
     */
    private static String kindOf(URI uri) {
        String rest = uri.toString().substring(URN_PREFIX.length());
        int sep = rest.indexOf(':');
        return sep < 0 ? rest : rest.substring(0, sep);
    }

    /**
     * Parser helper: extracts the URI ref segment while preserving dotted or dashed domain ids.
     */
    private static String refOf(URI uri) {
        String rest = uri.toString().substring(URN_PREFIX.length());
        int sep = rest.indexOf(':');
        return sep < 0 ? rest : rest.substring(sep + 1);
    }
}
