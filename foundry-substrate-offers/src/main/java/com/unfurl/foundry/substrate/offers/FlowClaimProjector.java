package com.unfurl.foundry.substrate.offers;

import com.unfurl.dcp.claim.Claim;
import com.unfurl.dcp.claim.ClaimMetadata;
import com.unfurl.dcp.claim.ComponentKind;
import com.unfurl.dcp.claim.Dependencies;
import com.unfurl.dcp.claim.Identity;
import com.unfurl.dcp.claim.IntegrationPorts;
import com.unfurl.dcp.projection.DcpProjectionProjector;
import com.unfurl.substrate.domain.NodeDefinition;
import com.unfurl.substrate.domain.NodeType;
import com.unfurl.substrate.domain.WorkflowDefinition;

import java.net.URI;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Projects the flow/workflow substrate composition
 * (Workflow &rarr; {Node &rarr; (uses) Component/Agent, Node(SUBGRAPH) &rarr; Sub-Workflow}) into DCP
 * {@link Claim}s whose {@code metadata.extensions} carry containment via
 * {@link DcpProjectionProjector#EXT_CONTAINS}.
 *
 * <p>Mirrors {@link FoundryClaimProjector} for the agent substrate. {@code WorkflowDefinition} contains
 * its nodes; each node contains the target named by {@link NodeDefinition#uses()} (a component, or a
 * sub-workflow for {@link NodeType#SUBGRAPH}); data-flow {@code Reference}s are not containment and are
 * ignored. Recurses into sub-workflows (cycle-guarded). A node whose {@code uses} begins with
 * {@value #AGENT_USES_PREFIX} bridges to the foundry agent graph using the shared
 * {@link FoundryClaimProjector#URN_PREFIX} scheme, so a combined claim map drills flow &rarr; agent
 * &rarr; tool in the one navigator.
 */
public final class FlowClaimProjector {
    public static final String URN_PREFIX = "urn:unfurl:flow:";
    public static final String AGENT_USES_PREFIX = "agent:";
    public static final String LEVEL_WORKFLOW = "WORKFLOW";
    public static final String LEVEL_NODE = "NODE";
    public static final String LEVEL_COMPONENT = "COMPONENT";
    public static final String LEVEL_AGENT = "AGENT";

    /**
     * Project a workflow's subtree into a claim map keyed by claim URI. Pass {@link #workflowUri(String)}
     * of the workflow as the projection root to {@link DcpProjectionProjector}.
     *
     * @param subWorkflowsById sub-workflows referenced by {@code SUBGRAPH} nodes, keyed by their
     *                         {@code uses} value; missing entries become leaf placeholders.
     */
    public Map<URI, Claim> project(WorkflowDefinition workflow, Map<String, WorkflowDefinition> subWorkflowsById) {
        Map<URI, Claim> claims = new LinkedHashMap<>();
        addWorkflow(workflow, subWorkflowsById, claims, new HashSet<>());
        return claims;
    }

/**
 * Implements the addWorkflow helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private void addWorkflow(
            WorkflowDefinition workflow,
            Map<String, WorkflowDefinition> subWorkflowsById,
            Map<URI, Claim> claims,
            Set<String> visited) {
        if (!visited.add(workflow.id())) {
            return; // already expanding/expanded this workflow (cycle / shared subgraph)
        }
        URI workflowUri = workflowUri(workflow.id());
        LinkedHashSet<URI> nodeUris = new LinkedHashSet<>();
        for (NodeDefinition node : workflow.nodes()) {
            URI nodeUri = nodeUri(workflow.id(), node.id());
            nodeUris.add(nodeUri);
            LinkedHashSet<URI> nodeChildren = new LinkedHashSet<>();
            String uses = node.uses();
            if (node.type() == NodeType.SUBGRAPH && uses != null && !uses.isBlank()) {
                URI subUri = workflowUri(uses);
                nodeChildren.add(subUri);
                WorkflowDefinition sub = subWorkflowsById == null ? null : subWorkflowsById.get(uses);
                if (sub != null) {
                    addWorkflow(sub, subWorkflowsById, claims, visited);
                } else {
                    claims.putIfAbsent(subUri, claim(subUri, uses, LEVEL_WORKFLOW, "WORKFLOW",
                            ComponentKind.INTELLIGENT_COMPONENT, List.of()));
                }
            } else if (uses != null && uses.startsWith(AGENT_USES_PREFIX)) {
                String agentId = uses.substring(AGENT_USES_PREFIX.length());
                URI agentUri = URI.create(FoundryClaimProjector.URN_PREFIX + "agent:" + agentId);
                nodeChildren.add(agentUri);
                // Bridge stub: a real agent claim (with its own children) is supplied by
                // FoundryClaimProjector when the two maps are merged; this keeps the edge non-dangling.
                claims.putIfAbsent(agentUri, claim(agentUri, agentId, LEVEL_AGENT, "AGENT",
                        ComponentKind.INTELLIGENT_COMPONENT, List.of()));
            } else if (uses != null && !uses.isBlank()) {
                URI componentUri = componentUri(uses);
                nodeChildren.add(componentUri);
                claims.putIfAbsent(componentUri, claim(componentUri, uses, LEVEL_COMPONENT, "COMPONENT",
                        ComponentKind.COMPONENT, List.of()));
            }
            claims.put(nodeUri, claim(nodeUri, node.name(), LEVEL_NODE, node.type().name(),
                    ComponentKind.COMPONENT, List.copyOf(nodeChildren)));
        }
        claims.put(workflowUri, claim(workflowUri, workflow.id(), LEVEL_WORKFLOW, "WORKFLOW",
                ComponentKind.INTELLIGENT_COMPONENT, List.copyOf(nodeUris)));
    }

/**
 * Factory method: creates the claim result while keeping caller-facing defaults and validation in one place.
 */
    private Claim claim(URI uri, String label, String level, String dcpType, ComponentKind kind, List<URI> children) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("level", level);
        extensions.put("dcpType", dcpType);
        extensions.put(DcpProjectionProjector.EXT_CONTAINS,
                children.stream().map(URI::toString).sorted().toList());
        return new Claim(
                new Identity(uri, label == null || label.isBlank() ? uri.toString() : label, kind,
                        "1.0.0", "Unfurl", URI.create("urn:unfurl")),
                null,
                List.of(),
                new Dependencies(List.of()),
                List.of(),
                null,
                null,
                new IntegrationPorts(Map.of()),
                AiOffers.faultPolicyFor(List.of()),
                new ClaimMetadata("0.2.0", "1.0.0", Instant.now(), extensions));
    }

/**
 * Implements the workflowUri helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    public URI workflowUri(String id) {
        return URI.create(URN_PREFIX + "workflow:" + id);
    }

/**
 * Implements the nodeUri helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    public URI nodeUri(String workflowId, String nodeId) {
        return URI.create(URN_PREFIX + "node:" + workflowId + "." + nodeId);
    }

/**
 * Implements the componentUri helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    public URI componentUri(String uses) {
        return URI.create(URN_PREFIX + "component:" + uses);
    }
}
