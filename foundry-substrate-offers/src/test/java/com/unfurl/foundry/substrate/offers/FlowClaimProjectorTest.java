package com.unfurl.foundry.substrate.offers;

import com.unfurl.dcp.claim.Claim;
import com.unfurl.dcp.projection.DcpProjection;
import com.unfurl.dcp.projection.DcpProjectionProjector;
import com.unfurl.dcp.projection.DcpProjectionRequest;
import com.unfurl.substrate.domain.NodeDefinition;
import com.unfurl.substrate.domain.NodeType;
import com.unfurl.substrate.domain.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FlowClaimProjectorTest {

    private final FlowClaimProjector projector = new FlowClaimProjector();

    private NodeDefinition node(String id, NodeType type, String uses) {
        return new NodeDefinition(id, type, uses, null, null, null, null, null, null, null, null, null, null, null,
                null, null, false);
    }

    private WorkflowDefinition workflow(String id, List<NodeDefinition> nodes) {
        return new WorkflowDefinition(id, "1.0.0", null, null, null, null, null, nodes, List.of());
    }

    private WorkflowDefinition orderFlow() {
        return workflow("order-flow", List.of(
                node("store", NodeType.ACTION, "storage-s3"),
                node("sub", NodeType.SUBGRAPH, "fulfillment-flow"),
                node("authoring", NodeType.ACTION, "agent:fabric-authoring"),
                node("route", NodeType.ROUTER, null)));
    }

    private Map<String, WorkflowDefinition> subWorkflows() {
        return Map.of("fulfillment-flow", workflow("fulfillment-flow", List.of(
                node("ship", NodeType.ACTION, "shipping-svc"))));
    }

    @Test
    void workflowContainsItsNodesAndNodesContainTheirUsesTarget() {
        Map<URI, Claim> claims = projector.project(orderFlow(), subWorkflows());

        @SuppressWarnings("unchecked")
        List<String> workflowContains = (List<String>) claims.get(projector.workflowUri("order-flow"))
                .metadata().extensions().get("contains");
        assertThat(workflowContains).contains(
                projector.nodeUri("order-flow", "store").toString(),
                projector.nodeUri("order-flow", "sub").toString(),
                projector.nodeUri("order-flow", "authoring").toString(),
                projector.nodeUri("order-flow", "route").toString());

        @SuppressWarnings("unchecked")
        List<String> storeContains = (List<String>) claims.get(projector.nodeUri("order-flow", "store"))
                .metadata().extensions().get("contains");
        assertThat(storeContains).containsExactly(projector.componentUri("storage-s3").toString());
    }

    @Test
    void subgraphNodeRecursesIntoTheSubWorkflow() {
        Map<URI, Claim> claims = projector.project(orderFlow(), subWorkflows());

        @SuppressWarnings("unchecked")
        List<String> subNodeContains = (List<String>) claims.get(projector.nodeUri("order-flow", "sub"))
                .metadata().extensions().get("contains");
        assertThat(subNodeContains).containsExactly(projector.workflowUri("fulfillment-flow").toString());

        assertThat(claims).containsKey(projector.workflowUri("fulfillment-flow"));
        assertThat(claims).containsKey(projector.nodeUri("fulfillment-flow", "ship"));
        assertThat(claims).containsKey(projector.componentUri("shipping-svc"));
    }

    @Test
    void agentUsesBridgesToTheFoundryAgentUrn() {
        Map<URI, Claim> claims = projector.project(orderFlow(), subWorkflows());

        URI agentUri = URI.create(FoundryClaimProjector.URN_PREFIX + "agent:fabric-authoring");
        @SuppressWarnings("unchecked")
        List<String> authoringContains = (List<String>) claims.get(projector.nodeUri("order-flow", "authoring"))
                .metadata().extensions().get("contains");
        assertThat(authoringContains).containsExactly(agentUri.toString());
        assertThat(claims).containsKey(agentUri); // non-dangling bridge stub; real claim merges from FoundryClaimProjector
    }

    @Test
    void controlNodeWithNoUsesIsALeaf() {
        Map<URI, Claim> claims = projector.project(orderFlow(), subWorkflows());

        @SuppressWarnings("unchecked")
        List<String> routeContains = (List<String>) claims.get(projector.nodeUri("order-flow", "route"))
                .metadata().extensions().get("contains");
        assertThat(routeContains).isEmpty();
    }

    @Test
    void projectsToMultiLevelDepthThroughTheDcpProjectorWithNoDangling() {
        Map<URI, Claim> claims = projector.project(orderFlow(), subWorkflows());
        URI root = projector.workflowUri("order-flow");

        DcpProjection projection = new DcpProjectionProjector().project(
                new DcpProjectionRequest(claims.get(root), claims, root, 16, 512));

        // workflow(0) -> sub node(1) -> fulfillment-flow(2) -> ship node(3) -> shipping-svc(4)
        assertThat(projection.warnings()).isEmpty();
        assertThat(projection.nodes()).anyMatch(node -> node.depth() >= 3);
    }
}
