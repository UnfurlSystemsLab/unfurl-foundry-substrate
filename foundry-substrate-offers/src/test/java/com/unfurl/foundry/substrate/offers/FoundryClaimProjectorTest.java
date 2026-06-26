package com.unfurl.foundry.substrate.offers;

import com.unfurl.dcp.claim.Claim;
import com.unfurl.dcp.projection.DcpProjection;
import com.unfurl.dcp.projection.DcpProjectionProjector;
import com.unfurl.dcp.projection.DcpProjectionRequest;
import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.agent.AgentPhase;
import com.unfurl.foundry.substrate.agent.BudgetPolicy;
import com.unfurl.foundry.substrate.skill.SkillDefinition;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FoundryClaimProjectorTest {

    private final FoundryClaimProjector projector = new FoundryClaimProjector();

    private AgentDefinition agent() {
        AgentPhase phase = new AgentPhase(
                "clarify",
                "main-prompt",   // promptTemplateRef
                "gemini",        // modelRef
                List.of("search"), // allowedToolRefs
                "kb",            // ragQueryRef
                Map.of(),
                Map.of(),
                List.of(),
                2);
        return new AgentDefinition(
                "fabric-authoring",
                "1.0.0",
                Map.of(),
                List.of(phase),
                List.of(),
                Map.of(),
                "gemini",                 // defaultModelRef
                List.of("catalog-query"), // toolRefs
                BudgetPolicy.none(),
                List.of("docs-skill"));   // skillRefs
    }

    private Map<String, SkillDefinition> skills() {
        SkillDefinition skill = new SkillDefinition(
                "docs-skill", "1.0.0", "docs grounding",
                List.of("search"),     // toolRefs
                "skill-prompt",        // promptFragmentRef
                List.of("kb"),         // ragSourceRefs
                "gemini",              // defaultModelRef
                null, null, null, null, null);
        return Map.of("docs-skill", skill);
    }

    @Test
    void agentClaimContainsItsRefsAsContainment() {
        Map<URI, Claim> claims = projector.project(agent(), skills(), Map.of());

        Claim agentClaim = claims.get(projector.agentUri("fabric-authoring"));
        assertThat(agentClaim).isNotNull();
        @SuppressWarnings("unchecked")
        List<String> contains = (List<String>) agentClaim.metadata().extensions().get("contains");
        assertThat(contains).contains(
                projector.toolUri("catalog-query").toString(),
                projector.skillUri("docs-skill").toString(),
                projector.modelUri("gemini").toString(),
                projector.promptUri("main-prompt").toString(),
                projector.ragUri("kb").toString(),
                projector.toolUri("search").toString());
        assertThat(agentClaim.metadata().extensions()).containsEntry("level", "AGENT");
    }

    @Test
    void skillExpandsIntoItsOwnContainedChildren() {
        Map<URI, Claim> claims = projector.project(agent(), skills(), Map.of());

        Claim skillClaim = claims.get(projector.skillUri("docs-skill"));
        assertThat(skillClaim).isNotNull();
        @SuppressWarnings("unchecked")
        List<String> contains = (List<String>) skillClaim.metadata().extensions().get("contains");
        assertThat(contains).contains(
                projector.toolUri("search").toString(),
                projector.promptUri("skill-prompt").toString(),
                projector.ragUri("kb").toString(),
                projector.modelUri("gemini").toString());
    }

    @Test
    void everyReferencedChildHasAClaim() {
        Map<URI, Claim> claims = projector.project(agent(), skills(), Map.of());

        // No dangling contains URIs: every URI referenced anywhere must have a claim.
        for (Claim claim : claims.values()) {
            List<?> contains = (List<?>) claim.metadata().extensions().getOrDefault("contains", List.of());
            for (Object child : contains) {
                assertThat(claims).containsKey(URI.create((String) child));
            }
        }
    }

    @Test
    void projectsToMultiLevelDepthThroughTheDcpProjector() {
        Map<URI, Claim> claims = projector.project(agent(), skills(), Map.of());
        URI root = projector.agentUri("fabric-authoring");

        DcpProjection projection = new DcpProjectionProjector().project(
                new DcpProjectionRequest(claims.get(root), claims, root, 16, 512));

        // Agent (depth 0) -> Skill (depth 1) -> Skill's tool (depth 2): recursive, no dangling warnings.
        assertThat(projection.warnings()).isEmpty();
        assertThat(projection.nodes()).anyMatch(node -> node.depth() >= 2);
        assertThat(projection.edges()).anyMatch(edge ->
                edge.fromClaimUri().equals(root)
                        && edge.toClaimUri().equals(projector.skillUri("docs-skill"))
                        && edge.relationship().equals("CONTAINS"));
    }
}
