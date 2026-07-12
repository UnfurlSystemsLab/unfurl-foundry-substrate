package com.unfurl.foundry.substrate.offers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.unfurl.dcp.claim.Claim;
import com.unfurl.dcp.claim.ClaimMetadata;
import com.unfurl.dcp.claim.ComponentKind;
import com.unfurl.dcp.claim.Dependencies;
import com.unfurl.dcp.claim.Identity;
import com.unfurl.dcp.claim.IntegrationPorts;
import com.unfurl.dcp.projection.DcpProjectionProjector;
import com.unfurl.foundry.substrate.agent.AgentDefinition;
import com.unfurl.foundry.substrate.skill.SkillDefinition;
import com.unfurl.foundry.substrate.tool.ToolDefinition;
import com.unfurl.substrate.domain.NodeDefinition;
import com.unfurl.substrate.domain.WorkflowDefinition;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * CLI Adapter: converts Flow workflow YAML and Foundry agent YAML into the JSON request accepted by
 * Fabric Studio's {@code dynamic-dcp/project} route.
 *
 * <p>The adapter keeps domain ownership in Flow/Foundry and emits only DCP claims for Fabric. It also
 * handles the Flowfoundry seed case where a workflow node says {@code uses: agent.run}: when exactly
 * one agent definition is supplied, that capability-style node is normalized to {@code agent:<id>} for
 * projection so the Flow graph can bridge to the concrete Foundry agent claim.
 */
public final class RecursiveProjectionRequestCli {
    private static final URI DEFAULT_ROOT_URI = URI.create("urn:unfurl:projection:flowfoundry");
    private static final String DEFAULT_ROOT_LABEL = "Flowfoundry Recursive Projection";

    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;
    private final FlowClaimProjector flowProjector;
    private final FoundryClaimProjector foundryProjector;

    /**
     * Constructs the CLI adapter with Jackson mappers and projector strategies required for the bridge.
     */
    public RecursiveProjectionRequestCli() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
        this.jsonMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
        this.flowProjector = new FlowClaimProjector();
        this.foundryProjector = new FoundryClaimProjector();
    }

    /**
     * Entry point: runs the bridge and exits non-zero only when arguments or source files are invalid.
     */
    public static void main(String[] args) throws IOException {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /**
     * Testable command runner: parses CLI arguments, reads sources, and writes the projection request.
     */
    public static int run(String[] args, PrintStream out, PrintStream err) throws IOException {
        RecursiveProjectionRequestCli cli = new RecursiveProjectionRequestCli();
        try {
            ProjectionRequest request = cli.buildRequest(Arguments.parse(args));
            if (request.output() == null) {
                cli.jsonMapper.writeValue(out, request);
            } else {
                Files.createDirectories(request.output().toAbsolutePath().getParent());
                cli.jsonMapper.writeValue(request.output().toFile(), request);
            }
            return 0;
        } catch (IllegalArgumentException ex) {
            err.println(ex.getMessage());
            err.println(Arguments.usage());
            return 2;
        }
    }

    /**
     * Adapter method: reads Flow/Foundry source files and merges their projector claim maps under an
     * aggregate root claim that Fabric can project without domain-specific dependencies.
     */
    private ProjectionRequest buildRequest(Arguments arguments) throws IOException {
        List<WorkflowDefinition> workflows = readAll(arguments.workflowFiles(), WorkflowDefinition.class);
        List<AgentDefinition> agents = readAll(arguments.agentFiles(), AgentDefinition.class);
        List<WorkflowDefinition> normalizedWorkflows = normalizeAgentRunUses(workflows, agents);

        Map<URI, Claim> claims = new LinkedHashMap<>();
        LinkedHashSet<URI> rootChildren = new LinkedHashSet<>();

        if (!normalizedWorkflows.isEmpty()) {
            WorkflowDefinition primary = normalizedWorkflows.getFirst();
            Map<String, WorkflowDefinition> subWorkflows = new LinkedHashMap<>();
            for (int i = 1; i < normalizedWorkflows.size(); i++) {
                subWorkflows.put(normalizedWorkflows.get(i).id(), normalizedWorkflows.get(i));
            }
            claims.putAll(flowProjector.project(primary, subWorkflows));
            rootChildren.add(flowProjector.workflowUri(primary.id()));
        }

        if (!agents.isEmpty()) {
            URI foundryRoot = arguments.foundryUri();
            claims.putAll(foundryProjector.projectFoundry(
                    foundryRoot,
                    arguments.foundryLabel(),
                    agents,
                    Map.<String, SkillDefinition>of(),
                    Map.<String, ToolDefinition>of()));
            rootChildren.add(foundryRoot);
        }

        if (rootChildren.isEmpty()) {
            throw new IllegalArgumentException("At least one --workflow or --agent file is required.");
        }

        URI rootUri = arguments.rootUri();
        claims.put(rootUri, aggregateRoot(rootUri, arguments.rootLabel(), List.copyOf(rootChildren)));
        URI focusUri = arguments.focusUri() == null ? rootUri : arguments.focusUri();
        return new ProjectionRequest(rootUri.toString(), focusUri.toString(), stringifyKeys(claims), arguments.output());
    }

    /**
     * YAML loader: reads a homogeneous list of domain records using the configured mapper.
     */
    private <T> List<T> readAll(List<Path> paths, Class<T> type) throws IOException {
        List<T> values = new ArrayList<>();
        for (Path path : paths) {
            values.add(yamlMapper.readValue(path.toFile(), type));
        }
        return List.copyOf(values);
    }

    /**
     * Adapter method: rewrites capability-style {@code agent.run} uses to the supplied concrete agent
     * id when the source set makes that binding unambiguous.
     */
    private static List<WorkflowDefinition> normalizeAgentRunUses(
            List<WorkflowDefinition> workflows,
            List<AgentDefinition> agents) {
        if (workflows.isEmpty() || agents.size() != 1) {
            return workflows;
        }
        String concreteUses = FlowClaimProjector.AGENT_USES_PREFIX + agents.getFirst().id();
        List<WorkflowDefinition> normalized = new ArrayList<>();
        for (WorkflowDefinition workflow : workflows) {
            List<NodeDefinition> nodes = new ArrayList<>();
            for (NodeDefinition node : workflow.nodes()) {
                if ("agent.run".equals(node.uses())) {
                    nodes.add(new NodeDefinition(
                            node.id(),
                            node.type(),
                            concreteUses,
                            node.name(),
                            node.description(),
                            node.config(),
                            node.input(),
                            node.outputMapping(),
                            node.dependencies(),
                            node.condition(),
                            node.loop(),
                            node.saga(),
                            node.compensation(),
                            node.onFailure(),
                            node.retry(),
                            node.timeout(),
                            node.template()));
                } else {
                    nodes.add(node);
                }
            }
            normalized.add(new WorkflowDefinition(
                    workflow.id(),
                    workflow.version(),
                    workflow.metadata(),
                    workflow.inputSchema(),
                    workflow.outputSchema(),
                    workflow.policies(),
                    workflow.triggers(),
                    nodes,
                    workflow.edges()));
        }
        return List.copyOf(normalized);
    }

    /**
     * Factory method: builds the aggregate projection root using DCP containment metadata.
     */
    private static Claim aggregateRoot(URI uri, String label, List<URI> children) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("level", "ROOT");
        extensions.put("dcpType", "ROOT");
        extensions.put(DcpProjectionProjector.EXT_CONTAINS,
                children.stream().map(URI::toString).sorted().toList());
        return new Claim(
                new Identity(uri, label, ComponentKind.INFRASTRUCTURE, "1.0.0", "Unfurl", URI.create("urn:unfurl")),
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
     * Wire helper: converts claim keys to strings because Fabric's request DTO receives a JSON object.
     */
    private static Map<String, Claim> stringifyKeys(Map<URI, Claim> claims) {
        Map<String, Claim> byString = new LinkedHashMap<>();
        claims.forEach((uri, claim) -> byString.put(uri.toString(), claim));
        return byString;
    }

    /**
     * Wire DTO: mirrors Fabric Studio's {@code StudioDcpProjectionRequest}; {@code output} is local
     * CLI state and is ignored by JSON serialization through its accessor exclusion.
     */
    private record ProjectionRequest(
            String rootClaimUri,
            String focusClaimUri,
            Map<String, Claim> claimsByUri,
            @com.fasterxml.jackson.annotation.JsonIgnore Path output
    ) {
    }

    /**
     * Parser DTO: owns deterministic command-line parsing for the bridge without adding a CLI library.
     */
    private record Arguments(
            List<Path> workflowFiles,
            List<Path> agentFiles,
            URI rootUri,
            URI focusUri,
            String rootLabel,
            URI foundryUri,
            String foundryLabel,
            Path output
    ) {
        /**
         * Parser: converts repeatable file flags and optional URI/label flags into validated arguments.
         */
        static Arguments parse(String[] args) {
            List<Path> workflowFiles = new ArrayList<>();
            List<Path> agentFiles = new ArrayList<>();
            URI rootUri = DEFAULT_ROOT_URI;
            URI focusUri = null;
            String rootLabel = DEFAULT_ROOT_LABEL;
            URI foundryUri = URI.create(FoundryClaimProjector.URN_PREFIX + "deployment:foundry");
            String foundryLabel = "Foundry Deployment";
            Path output = null;

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--workflow" -> workflowFiles.add(Path.of(requireValue(args, ++i, arg)));
                    case "--agent" -> agentFiles.add(Path.of(requireValue(args, ++i, arg)));
                    case "--root-uri" -> rootUri = URI.create(requireValue(args, ++i, arg));
                    case "--focus-uri" -> focusUri = URI.create(requireValue(args, ++i, arg));
                    case "--root-label" -> rootLabel = requireValue(args, ++i, arg);
                    case "--foundry-uri" -> foundryUri = URI.create(requireValue(args, ++i, arg));
                    case "--foundry-label" -> foundryLabel = requireValue(args, ++i, arg);
                    case "--output" -> output = Path.of(requireValue(args, ++i, arg));
                    default -> throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }

            return new Arguments(
                    List.copyOf(workflowFiles),
                    List.copyOf(agentFiles),
                    rootUri,
                    focusUri,
                    rootLabel,
                    foundryUri,
                    foundryLabel,
                    output);
        }

        /**
         * Parser helper: returns a required argument value and reports a clean usage error when absent.
         */
        private static String requireValue(String[] args, int index, String flag) {
            if (index >= args.length || args[index].startsWith("--")) {
                throw new IllegalArgumentException("Missing value for " + flag);
            }
            return args[index];
        }

        /**
         * Parser helper: returns compact usage text for failed invocations.
         */
        private static String usage() {
            return """
                    Usage: RecursiveProjectionRequestCli [--workflow file.yaml]... [--agent file.yaml]...
                           [--root-uri urn:...] [--focus-uri urn:...] [--root-label label]
                           [--foundry-uri urn:...] [--foundry-label label] [--output file.json]
                    """;
        }
    }
}
