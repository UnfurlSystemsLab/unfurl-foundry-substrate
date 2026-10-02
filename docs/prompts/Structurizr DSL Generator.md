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

## 4. Integration Ports & Extensibility

The substrate is extended almost entirely through ports that a host implements. Model every
**open DCP port**, meaning an integration concern the library declares but does not satisfy itself,
as an explicit extensibility option. This lets readers see where authentication, authorization,
monitoring, cost, tracking and similar concerns plug in.

### 4.1 Sources

- **DCP vocabulary for integration ports** (sibling checkout `../dcp`, if present):
  - `../dcp/docs/elements/integration-ports.md`: the common ports `authentication`, `authorization`,
    `telemetry`, `monitoring`, `secrets`, `configuration` and `ai`;
  - `../dcp/docs/DCP-VOCABULARY.md`;
  - `../dcp/docs/HLD-C-dcp-v0.2-internal.md`: components consume the customer's identity provider
    through a port, emit to the customer's audit sink, and export telemetry to the customer's
    collector.
- **Every claim** in `*/src/main/resources/META-INF/unfurl-catalog.yaml`:
  - `needs` entries, especially those with `owner=host` or `owner=customer-*`, and their `shape=`
    port type;
  - any `integration_ports.ports` block.
- **Port interfaces:** `foundry-substrate-ports` (`ports`, `guardrail`), plus `ExternalCallBoundary`,
  `AgentRunStore` and `AgentHarnessStateStore` in the engine.
- **Defaults:** the in-memory and no-op defaults, and the null-port fallbacks in
  `EmbeddedAgentRuntime` (e.g. `defaultEventSink()`, `defaultStore()`).

### 4.2 Extensibility matrix (seed — verify every row)

Classify each concern with exactly one **status**:
- `Implemented default`: a usable default ships in this repository.
- `No-op / in-memory default`: it runs but does not persist or export anything.
- `Open port`: an interface exists, with no implementation here.
- `Host-owned`: the library consumes the concern but defines no port for it.
- `Not present`: neither a port nor a default exists. List it as a gap, and never invent it.

| Concern (DCP port) | Substrate seam | Shipped default | Expected status |
|---|---|---|---|
| Authentication (`authentication`) | Consumes `com.unfurl.substrate.policy.ExecutionContext` (tenantId, userId, roles, permissions, correlationId, requestId, traceContext) from the host | None | Host-owned |
| Authorization (`authorization`) | `guardrail.PermissionBridge` / `PermissionDecision`; `ToolCallInterceptor(Chain)` returning `ToolCallDecision` (ALLOW / DENY / REQUIRE_APPROVAL); `ToolCallScope` | Verify | Open port |
| Cost & budgets | `guardrail.CostGuardrail`, `CostGuardrailContext`, `GuardrailDecision`; `BudgetPolicy.lowerOf`; `runstate.CostAccounting`; `TOKENS_CONSUMED` events | `BudgetPolicyCostGuardrail` (no I/O, current-run snapshot only) | Implemented default; persistent quota and billing are host-owned |
| Monitoring / telemetry / tracking (`monitoring`, `telemetry`) | `AgentEventSink` with `AgentEventType` (agent, phase, model, token, tool, RAG, guardrail and validation events); `CorrectionProgressObserver`; `ExecutionContext.traceContext` | Verify `defaultEventSink()` | No-op / in-memory default |
| Audit & durable tracking | `ExternalCallBoundary` (host journals each provider/tool/child/workflow call); `AgentRunStore`; `AgentHarnessStateStore` | Direct boundary; in-memory stores | No-op / in-memory default |
| AI model access (`ai`) | `ModelProvider`, `ProviderRegistry`; needs `model-provider@v1`, `spring-ai.chat-client@v1?owner=host` | Spring AI adapter over host beans | Open port + adapter |
| Embeddings, vector, RAG (`ai`) | `EmbeddingProvider`, `VectorStore`, `RagRetriever`; needs `embedding-provider@v1`, `vector-store@v1`, `rag.corpus@v1?owner=host` | Spring AI adapter; test fixtures | Open port + adapter |
| Tools | `ToolRegistry`, `ToolExecutor`; need `tool.implementation@v1?owner=host` | Test fixtures only | Open port |
| Context & request shaping | `ContextSelector`, `ContextResourceProvider`, `ModelRequestProjector` | `ModelRequestProjector.identity()` | Implemented default (identity) |
| Validation | `OutputSchemaValidator`, `SemanticValidator(Registry)` | Verify | Open port |
| Durable continuation | `AgentToolContinuation`, `SuspendedToolExecutor`, `AgentHarnessChildContinuation` | No default executor, by design | Open port |
| Secrets (`secrets`), configuration (`configuration`) | No port: the host constructs providers with their own credentials and configuration | None | Host-owned |

### 4.3 How to model it

- **Extension points:** model each one as a component in the module that defines its port, with:
  - the tag `Extension Point` and shape `Hexagon`;
  - properties `dcpPort`, `portTypes`, `status`, `defaultImplementation` and `claimNeed`
    (the exact need string, when one exists).
- **Customer-supplied systems:** for each `Open port` or `Host-owned` concern, add an external
  system tagged `Customer-supplied` that represents the implementation a host plugs in. Examples:
  - "Host Identity & Access (OIDC / IdP)";
  - "Host Policy Engine";
  - "Host Telemetry Collector (OTLP)";
  - "Host Audit Store";
  - "Host Cost & Usage Ledger";
  - "Host Model / Vector Providers";
  - "Host Tool Implementations".
- **Extension edges:** connect the customer-supplied system to the extension point, tagged
  `Extension` and drawn dashed, using `"Implements <PortType>" "Java SPI (host-supplied, in-process)"`.
  Name a concrete protocol such as OTLP or OIDC only if this repository's docs or code name it;
  otherwise keep the label protocol-neutral.
- **"Extensibility" view:** a dedicated component or module view that shows only the extension points,
  their modules and the customer-supplied systems. Keep it readable by giving each concern one
  extension-point component, with the individual port types listed in its description.

---

## 5. Structurizr DSL Requirements

- **One `workspace`** with `name` and `description` (stating the convention in §2),
  `!identifiers hierarchical`, one `model` and one `views` block. Use stable lowercase identifiers
  such as `substrate.engine` and `substrate.ports.continuation`.
- **Evidence comments:** put a `//` comment above every element and relationship citing its source,
  e.g. `// source: foundry-substrate-engine/pom.xml; ArchitectureTest.moduleEdgesMatchAllowedGraph`.
- **Tags and styles:**
  - Tags: `Library`, `Library Module`, `Sample Claim`, `Test Fixture`, `External Library`,
    `Host-owned`, `Extension Point`, `Customer-supplied`, `Extension`, `Planned`.
  - Shapes: `Component` for Library Module, `Folder` for Sample Claim, `Hexagon` for Extension Point,
    and grey for External Library.
  - Customer-supplied elements: a distinct outline colour.
  - Host-owned and Extension relationships: dashed.
- **Readability:**
  - Keep each component view to about 12 elements by collapsing families.
  - Give every view an explicit `include` and a `title`.
  - Use spacing values in `autoLayout` so nodes do not overlap.
- **No fabrication:** model only implemented types. Backlog items from
  `IMPLEMENTATION-agent-harness-contract-backlog.md` or the build spec that are not in code get the tag
  `Planned`. Show them only in a separate module view titled "Planned", and never in the as-built
  views.

---

## 6. Validation and Output

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
   - the verified **extensibility matrix** (§4.2) with the final status of each concern, plus every
     claim `need` with `owner=host` or `owner=customer-*` that no extension point covers;
   - omitted or unverifiable items;
   - the render command:
     `docker run -it --rm -p 8090:8080 -v <repo>/docs/architecture:/usr/local/structurizr structurizr/lite`.
