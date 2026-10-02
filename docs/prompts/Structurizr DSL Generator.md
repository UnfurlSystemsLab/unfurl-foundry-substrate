# Prompt: `unfurl-foundry-substrate` C4 Model → Structurizr DSL (`workspace.dsl`)

Analyze the `unfurl-foundry-substrate` repository and produce a multi-level **C4 architecture model**
of the library **as built**. Write the result as one valid **Structurizr DSL** file at
`docs/architecture/workspace.dsl` in this repository, then report what you modelled and what you could
not verify.

This repository is a **provider-neutral Java 21 library**, not a service. It has no `main` class, no
HTTP server, no Dockerfile and no deployment manifest. Do not invent containers that run on their own,
network listeners, databases, queues, Spring Boot, `application.yml` or vendor SDK calls. Every element
and every relationship label must be backed by evidence you actually read.

---

## 1. Discovery

### 1.1 Sources of truth (read in this order; code wins on conflict)

1. **Module graph:** the root `pom.xml` `<modules>` list and each module's `pom.xml` dependencies.
   Ignore `test`-scoped dependencies.
2. **Enforced architecture:**
   `foundry-substrate-engine/src/test/java/com/unfurl/foundry/substrate/architecture/ArchitectureTest.java`.
   Read every rule, especially:
   - `moduleEdgesMatchAllowedGraph`: the allowed module edges;
   - `productionCodeDoesNotUseSdkOrInfrastructureClients`: no SDK or network clients;
   - `productionCodeDoesNotImportHostProducts`: no imports of Foundry or Flow;
   - `dcpDependenciesStayInOffers`: `unfurl-dcp` only in `offers`;
   - `productionCodeDoesNotDependOnTestingFixtures`.

   The model must not contain an edge these rules forbid. Any POM edge that the allowed graph does
   not list must appear in your report.
3. **Code:** each module's packages under `src/main/java`, the public port interfaces in
   `foundry-substrate-ports`, and the runtime classes in `foundry-substrate-engine`. These include
   `EmbeddedAgentRuntime`, `EmbeddedAgentHarnessRuntime`, `SequentialAgentDelegate`,
   `ExternalCallBoundary`, `AgentHarnessStateStore` and the in-memory stores.
4. **Sample claims:** `sample-*/src/main/resources/META-INF/unfurl-catalog.yaml`. These are DCP
   catalog claims with no Java code.
5. **Design docs:** `docs/HLD-unfurl-foundry-substrate.md`, `docs/LLD-unfurl-foundry-substrate-java.md`,
   `docs/REPO-unfurl-foundry-substrate-build-spec.md`, `docs/use/README.md`,
   `docs/IMPLEMENTATION-agent-harness-contract-backlog.md`, `docs/RELIABILITY.md` and
   `docs/SECURITY.md`. Use them for intent and naming, but model only what the code implements.
6. **Consumers:** look for downstream usage in sibling checkouts if present (`../unfurl-foundry`,
   `../unfurl-flow`). Use their `pom.xml` files only to name which modules each consumer uses. Never
   model a consumer's internals.

### 1.2 Expected module baseline

Verify each row. If the code disagrees, follow the code and list the difference.

| Module | Expected role | Layer group |
|---|---|---|
| `foundry-substrate-domain` | Agent, harness, skill, prompt, model, tool, RAG, context, delegation, failure, terminal and run-state records | Domain & Events |
| `foundry-substrate-events` | AI event schema | Domain & Events |
| `foundry-substrate-ports` | Neutral ports (runtime, provider, tool, validation, context, continuation) and guardrail defaults | Ports |
| `foundry-substrate-prompt`, `-tools`, `-rag`, `-resolver`, `-serialization` | Small building blocks: prompt assembly, tool helpers, RAG helpers, `agentRef`/`toolRef` resolution, JSON/YAML codec | Building Blocks |
| `foundry-substrate-offers` | DCP offer fragments and `ContractInvocable` factories; the only module that imports `unfurl-dcp` | DCP Offers |
| `foundry-substrate-engine` | Embedded in-process agent and harness runtimes, sequential delegation, external-call boundary | Embedded Runtime |
| `foundry-substrate-springai-adapter` | Spring AI model, embedding and vector-store adapters over host-supplied beans | Adapters |
| `foundry-substrate-testing` | Downstream test fixtures (echo provider, static embedder, in-memory stores, recording tool executor) | Test Fixtures |
| `sample-chatbot-agent`, `sample-research-agent`, `sample-tool-using-agent` | Catalog-only DCP claims for Fabric composition | Samples |

Upstream libraries to show as external systems tagged `External Library`:
- `unfurl-substrate` modules (`substrate-domain`, `-events`, `-ports`, `-resolver`, `-composition-api`);
- `unfurl-dcp`;
- Spring AI (`spring-ai-model`, `spring-ai-vector-store`);
- Jackson.

---

## 2. C4 Modelling Convention for a Library Repository

Strict C4 treats a library as a component, not a container. Because this repository has no
deployables, apply the following documented convention and state it in the workspace `description`:

- **Software system:** `Unfurl Foundry Substrate` (tag `Library`).
- **Level 2 "containers":** one per published Maven module. Use the technology string
  `Java 21 library JAR (com.unfurl.foundry.substrate:<artifactId>)` and the tag `Library Module`.
  Sample modules use the tag `Sample Claim`, and `foundry-substrate-testing` uses `Test Fixture`.
- **Level 3 components:** package groupings and key types inside a module.
- **Relationships between modules:** label these `Maven compile dependency` and say what is used,
  e.g. `"Implements agent runtime ports" "Maven compile dependency"`.
- **In-process calls inside the engine:** label these `Java API (in-process)`.
- **No network edges from substrate code.** The substrate opens no sockets. Any vendor or network
  interaction belongs to a **host-supplied** implementation, so draw it from the host system and tag
  it `Host-owned`. For example, Spring AI beans supplied by the host call the model vendor. The
  `springai-adapter` → host edge reads `"Delegates to host-supplied Spring AI ChatModel / EmbeddingModel / VectorStore beans" "Java API (in-process)"`.

---

## 3. Diagram Levels

Use `autoLayout tb` unless stated otherwise.

1. **Level 1: System Context.** Show the library, the people and systems that use it, and what it
   depends on:
   - **Actor:** a Host Application Developer who embeds the library and supplies port
     implementations.
   - **Consumers:** `Unfurl Foundry` (agent product) and `Unfurl Flow` (workflow runtime), each
     labelled with the modules it consumes. Add a generic "Embedding Host Application" for
     third-party hosts.
   - **`Unfurl Fabric`** reads the sample claims and `META-INF/unfurl-catalog.yaml` at design time,
     via `"Scans DCP catalog claims" "DCP claim YAML (design time)"`.
   - **Upstream libraries:** the external libraries listed in §1.2.
   - **Model vendors:** reached only through the host, with `Host-owned` edges.
2. **Level 2: Module view** (`container` view). Show all modules grouped with `group` blocks by the
   layer column in §1.2, plus the upstream libraries. Every module-to-module edge must match
   `moduleEdgesMatchAllowedGraph`.
3. **Level 3: Component views**, one each:
   - **`foundry-substrate-ports`:** group the ports into families instead of one box per interface:
     - Runtime: `AgentRuntime`, `AgentHarnessRuntime`, `AgentDelegate`, `AgentDefinitionResolver`,
       `SkillRegistry`.
     - Providers: `ModelProvider`, `EmbeddingProvider`, `ProviderRegistry`, `VectorStore`,
       `RagRetriever`.
     - Tools and policy: `ToolRegistry`, `ToolExecutor`, `ToolCallInterceptor(Chain)`,
       `ToolCallScope`/`Decision`/`Request`/`Result`.
     - Validation: `OutputSchemaValidator`, `SemanticValidator(Registry)`, `ValidationResult`.
     - Context: `ContextSelector`, `ContextResourceProvider`, `ModelRequestProjector`.
     - Continuation: `AgentToolContinuation`, `SuspendedToolExecutor`,
       `AgentHarnessChildContinuation`, `CorrectionProgress`/`Observer`.
     - Events and guardrails: `AgentEventSink`, the `guardrail` package.
   - **`foundry-substrate-engine`:** `EmbeddedAgentRuntime`, `EmbeddedAgentHarnessRuntime`,
     `SequentialAgentDelegate`, `ExternalCallBoundary`, the harness/run state stores and their
     in-memory defaults, plus the ports each one calls.
   - **`foundry-substrate-offers`:** offer fragments, the `AgentInvocation`/`ToolInvocation`/
     `RagInvocation` `ContractInvocable`s and the `unfurl-dcp` types they implement.
   - **`foundry-substrate-springai-adapter`:** each adapter class mapped to the port it implements
     and the Spring AI type it wraps.
4. **Dynamic view: one embedded agent run.** Number each step, using in-process technology labels.
   Verify the order in `EmbeddedAgentRuntime` before writing it. The expected sequence is:
   1. The host calls `AgentRuntime.start`.
   2. Definition validation and resolution run.
   3. The prompt is assembled.
   4. `ModelRequestProjector` projects the request.
   5. The model call goes through `ExternalCallBoundary` to the host's `ModelProvider`.
   6. `ToolCallInterceptorChain` evaluates any tool call, then `ToolExecutor` runs it through
      `ExternalCallBoundary`.
   7. Output-schema and semantic validation and the correction loop run.
   8. The run state is saved to the `AgentRunStore`.
   9. Events go to `AgentEventSink`.

   A `REQUIRE_APPROVAL` tool decision produces a `WAITING` snapshot. Show this as an alternative
   step only if the code confirms it.

Do **not** create a deployment view. This repository deploys nothing. Note in the report that
distribution happens through GitHub Packages via the root aggregator's `publish-lab-maven.yml`, which
`.github/workflows/ci.yml` dispatches.

---

## 4. Structurizr DSL Requirements

- **One `workspace`** with `name` and `description` (stating the convention in §2),
  `!identifiers hierarchical`, one `model` and one `views` block. Use stable lowercase identifiers
  such as `substrate.engine` and `substrate.ports.continuation`.
- **Evidence comments:** put a `//` comment above every element and relationship citing its source,
  e.g. `// source: foundry-substrate-engine/pom.xml; ArchitectureTest.moduleEdgesMatchAllowedGraph`.
- **Tags and styles:**
  - Tags: `Library`, `Library Module`, `Sample Claim`, `Test Fixture`, `External Library`,
    `Host-owned`, `Planned`.
  - Shapes: `Component` for Library Module, `Folder` for Sample Claim, and grey for External Library.
  - Host-owned relationships: dashed.
- **Readability:**
  - Keep each component view to about 12 elements by collapsing families.
  - Give every view an explicit `include` and a `title`.
  - Use spacing values in `autoLayout` so nodes do not overlap.
- **No fabrication:** model only implemented types. Backlog items from
  `IMPLEMENTATION-agent-harness-contract-backlog.md` or the build spec that are not in code get the tag
  `Planned`. Show them only in a separate module view titled "Planned", and never in the as-built
  views.

---

## 5. Validation and Output

1. Write `docs/architecture/workspace.dsl`.
2. Validate it with whichever is available:
   - `structurizr-cli validate -workspace docs/architecture/workspace.dsl`;
   - the same command through the `structurizr/cli` Docker image;
   - the Structurizr CLI release zip run with `java -cp "lib/*" com.structurizr.cli.StructurizrCliApplication validate ...`.

   Prove the validator works by running it once against a deliberately broken copy. Fix all errors.
3. Optionally export to JSON (`export -format json`) and confirm no `Planned` element appears in an
   as-built view.
4. Report:
   - the verified module table and any differences from §1.2;
   - the module edges compared with `moduleEdgesMatchAllowedGraph`;
   - omitted or unverifiable items;
   - the render command:
     `docker run -it --rm -p 8090:8080 -v <repo>/docs/architecture:/usr/local/structurizr structurizr/lite`.
