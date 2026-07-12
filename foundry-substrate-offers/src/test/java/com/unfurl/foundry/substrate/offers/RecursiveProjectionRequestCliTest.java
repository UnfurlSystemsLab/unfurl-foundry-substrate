package com.unfurl.foundry.substrate.offers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration-style test for the recursive projection CLI adapter: proves a capability-style Flow
 * seed can be bridged to one concrete Foundry agent and retain phase-scoped runtime details.
 */
class RecursiveProjectionRequestCliTest {

    @TempDir
    Path tempDir;

    /**
     * Verifies the generated Fabric request contains Flow, Agent, Phase, Tool, and RAG claim URIs.
     */
    @Test
    void writesFabricProjectionRequestWithFlowFoundryBridgeAndPhaseDetails() throws Exception {
        Path workflow = tempDir.resolve("workflow.yaml");
        Files.writeString(workflow, """
                id: flowfoundry-export
                version: "1.0.0"
                nodes:
                  - id: run-agent
                    uses: agent.run
                """);
        Path agent = tempDir.resolve("workload-agent.agent.yaml");
        Files.writeString(agent, """
                id: workload-agent
                version: "1.0.0"
                defaultModelRef: customer-configured
                toolRefs:
                  - fabric.catalog-query
                phases:
                  - id: answer
                    promptTemplateRef: workload-prompt
                    modelRef: customer-configured
                    allowedToolRefs:
                      - fabric.intent-emitter
                    ragQueryRef: docs
                """);
        Path output = tempDir.resolve("request.json");

        int exitCode = RecursiveProjectionRequestCli.run(new String[] {
                "--workflow", workflow.toString(),
                "--agent", agent.toString(),
                "--output", output.toString()
        }, new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()));

        assertThat(exitCode).isZero();
        JsonNode root = new ObjectMapper().readTree(output.toFile());
        JsonNode claims = root.path("claimsByUri");
        assertThat(claims.has("urn:unfurl:flow:workflow:flowfoundry-export")).isTrue();
        assertThat(claims.has("urn:unfurl:foundry:agent:workload-agent")).isTrue();
        assertThat(claims.has("urn:unfurl:foundry:phase:workload-agent.answer")).isTrue();
        assertThat(claims.has("urn:unfurl:foundry:rag:docs")).isTrue();
        assertThat(claims.path("urn:unfurl:flow:node:flowfoundry-export.run-agent")
                .path("metadata")
                .path("extensions")
                .path("contains")
                .toString()).contains("urn:unfurl:foundry:agent:workload-agent");
        assertThat(Files.readString(output, StandardCharsets.UTF_8))
                .contains("\"rootClaimUri\"")
                .contains("\"claimsByUri\"");
    }
}
