# Low-Level Design: `unfurl-foundry-substrate` Java

**Document status:** Implementation LLD for the Java AI-substrate library path.
**Audience:** Engineers implementing `unfurl-foundry-substrate`.
**Primary sources:**

- `00-product-architecture.md`
- `HLD-unfurl-foundry-substrate.md`
- `LLD-unfurl-substrate-java.md` (the deterministic-side template this mirrors)
- `HLD-C-dcp-v0.2-internal.md`, `HLD-C2-dcp-schema-spec-updated.md`
- `HLD-B-intelligent-components-v3-GPT.md`

---

## Purpose And Sources

This document translates the foundry-substrate HLD into implementation-level design for the embedded AI-substrate library family. The layer is the AI peer of `unfurl-substrate`: a JDK 21 Maven multi-module repository under `com.unfurl.foundry.substrate` that depends on `unfurl-substrate` and (narrowly) on `unfurl-dcp`, with no internal dependency on any host product and no deployable runtime of its own. It publishes multiple narrow artifacts that version and release together.

`LLD-unfurl-substrate-java.md` is the structural template. Where this document diverges from it, the divergence is AI-specific (agents, tools, models, retrieval, embeddings) and never relaxes the substrate's enterprise posture.

The design preserves the enterprise posture of the deterministic substrate:

- No runtime callbacks to Unfurl.
- No Unfurl design-time AI/model reasoning in the perimeter; runtime model invocation is the customer's own, behind a port.
- No product-to-product shortcuts when co-packaged.
- Capability exposure and contract execution through the `unfurl-dcp` broker and `ContractInvocable` only.
- Audit and telemetry are shaped through ports; concrete bindings live outside substrate.
- Trust, signature verification, credential storage, and offline licensing are product/DCP/Fabric concerns, but substrate types carry the neutral metadata those layers need.

---

## Non-Goals And Enterprise Guardrails

`unfurl-foundry-substrate` must not implement:

- Concrete model, embedding, or vector SDKs/clients; HTTP clients; sockets; or any network transport.
- The DCP runtime composition broker (claim → disposition → contract → capability exposure). That is `unfurl-dcp`.
- Negotiation or accept/reject/refusal reasoning, or any intelligence in the runtime path. That is `unfurl-fabric`, at design time.
- DCP claim/contract/disposition schemas. Those are `unfurl-dcp`; this layer fills in AI offer fragments only.
- Per-tenant credential storage, encryption, rotation, or secret management.
- Cost aggregation/rollup, usage persistence, dashboards, quota/budget state, metering sinks, or billing/usage export.
- Durable, distributed, streaming, or parallel agent execution; retries; work queues; checkpoint stores.
- Model hosting, fine-tuning, LoRA training, or prompt-content policy.
- Servers, REST endpoints, containers, databases, cloud SDKs, or auth/OIDC implementations.

`unfurl-foundry-substrate` must provide:

- Stable agent/tool/model/rag/embedding domain shapes and AI execution-state metadata needed by flow and foundry.
- AI ports with no-op/fake defaults that perform no I/O and never phone home.
- AI capability offer fragments and `ContractInvocable` implementations the `unfurl-dcp` broker can register into a host `CapabilityRegistry`.
- A `correlationId` path through `ExecutionContext`, AI events, and contract invocation.
- A minimal in-process embedded agent runner sufficient to execute a multi-phase agent against injected ports, leaving durability and concrete providers to foundry.
- Cost/guardrail and permission **port** shapes, without enforcing budgets or policies itself.
- A `CostAccounting` shape and metering-grade event metadata (with attribution dimensions) sufficient for a reporting layer above to attribute and aggregate spend, without aggregating or persisting it here.

---

## Package And Module Design

`unfurl-foundry-substrate` is one cohesive layer, not one fat jar. It is a single repository, one coordinated version, and multiple publishable artifacts:

```text
group: com.unfurl.foundry.substrate
java: 21
root package: com.unfurl.foundry.substrate

artifacts:
  foundry-substrate-domain
  foundry-substrate-events
  foundry-substrate-ports
  foundry-substrate-prompt
  foundry-substrate-tools
  foundry-substrate-rag
  foundry-substrate-resolver
  foundry-substrate-offers
  foundry-substrate-engine
  foundry-substrate-testing
```

The split is load-bearing:

- Flow can depend on `foundry-substrate-ports` + `foundry-substrate-offers` to register AI capability without pulling the embedded runner.
- Adapters that implement model/embedding/vector ports depend on `foundry-substrate-ports` and avoid engine code.
- `unfurl-dcp` is imported by exactly one module (`foundry-substrate-offers`), keeping the DCP coupling auditable.
- ArchUnit and Maven enforcer checks enforce architecture as structure: no model SDK, no host imports, dcp confined to `offers`.

Module contents and dependency direction:

```text
foundry-substrate-domain
  packages:
    com.unfurl.foundry.substrate.agent
    com.unfurl.foundry.substrate.tool
    com.unfurl.foundry.substrate.model
    com.unfurl.foundry.substrate.rag
    com.unfurl.foundry.substrate.embedding
    com.unfurl.foundry.substrate.runstate
  contains:
    AgentDefinition, AgentPhase, ToolDefinition, Message, PromptTemplate,
    ModelRequest/ModelResponse, RagQuery/RagResult/Chunk, EmbeddingRequest/EmbeddingResult,
    AgentRunState, AgentPhaseState, ToolCall, CostAccounting, statuses, identities,
    AgentDefinitionValidator
  may depend on:
    substrate-domain, Jackson annotations/datatype support, Jakarta Validation API

foundry-substrate-events
  package:
    com.unfurl.foundry.substrate.events
  contains:
    AgentEventType and AI event builders (or AgentEvent over substrate Event)
  may depend on:
    substrate-events

foundry-substrate-ports
  packages:
    com.unfurl.foundry.substrate.ports
    com.unfurl.foundry.substrate.guardrail
  contains:
    ModelProvider, EmbeddingProvider, VectorStore, ToolExecutor, ToolRegistry,
    RagRetriever, AgentRuntime, ProviderRegistry, CostGuardrail, PermissionBridge,
    no-op/default port types
  may depend on:
    foundry-substrate-domain, substrate-ports, substrate-events

foundry-substrate-prompt
  package:
    com.unfurl.foundry.substrate.prompt
  contains:
    pure prompt/message assembly and template rendering services
  may depend on:
    foundry-substrate-domain

foundry-substrate-tools
  package:
    com.unfurl.foundry.substrate.tools
  contains:
    tool invocation request/result shapes, DefaultToolRegistry
  may depend on:
    foundry-substrate-ports

foundry-substrate-rag
  package:
    com.unfurl.foundry.substrate.rag
  contains:
    RAG query/result/provenance shapes, retrieval request model
  may depend on:
    foundry-substrate-ports

foundry-substrate-resolver
  package:
    com.unfurl.foundry.substrate.resolver
  contains:
    agentRef/toolRef reference resolution services and located resolution errors
  may depend on:
    foundry-substrate-domain, substrate-resolver

foundry-substrate-offers
  package:
    com.unfurl.foundry.substrate.offers
  contains:
    AI capability offer fragments (DCP claim shapes for agent.run/tool.call/rag.search/provider.call),
    AgentInvocation/ToolInvocation as ContractInvocable implementations
  may depend on:
    foundry-substrate-domain, unfurl-dcp, substrate-composition-api

foundry-substrate-engine
  package:
    com.unfurl.foundry.substrate.engine
  contains:
    EmbeddedAgentRuntime, in-memory AgentRunStore, no-op providers
  may depend on:
    foundry-substrate-domain, foundry-substrate-ports, foundry-substrate-prompt,
    foundry-substrate-tools, foundry-substrate-rag, foundry-substrate-resolver,
    foundry-substrate-offers, foundry-substrate-events, substrate-engine

foundry-substrate-testing
  package:
    com.unfurl.foundry.substrate.testing
  contains:
    EchoModelProvider, StaticEmbeddingProvider, InMemoryVectorStore,
    RecordingToolExecutor, NoopGuardrail, event collectors, fixtures
  may depend on:
    all foundry-substrate modules
```

Implementation rules:

- Prefer Java records for immutable data. Use immutable classes with builders only when Jackson/Jakarta Validation ergonomics require them.
- Use defensive copies for collections and map-like payloads.
- Use Jackson for JSON/YAML serialization and Jakarta Validation for object validation.
- Keep service classes stateless unless they are explicitly registry/store/runtime implementations.
- Production modules must not depend on `foundry-substrate-testing`.
- `unfurl-dcp` may be imported only by `foundry-substrate-offers` (and transitively by `foundry-substrate-engine`).
- No module may import `unfurl-flow`, `unfurl-foundry`, or `unfurl-fabric`.
- No module may import a model, embedding, or vector SDK, or an HTTP client.
- The parent Maven POM owns dependency/plugin versions and publishes all modules with the same version.

---

## Core Types And Interfaces

### Domain

Core domain types live under `com.unfurl.foundry.substrate.*`. Agent topology reuses the substrate's static-DAG model: an agent is a set of phases joined by conditional edges, not an open-ended reasoning loop.

`AgentDefinition`

- Fields: `id`, `version`, `metadata`, `phases`, `edges`, `inputSchema`, `defaultModelRef`, `toolRefs`, `budgetPolicy`.
- A multi-phase agent. `phases` are keyed by unique id after validation; `edges` are conditional, reusing the substrate `EdgeDefinition`/`ConditionDefinition` shapes.
- Topology is static. Runtime data affects routing only through conditional edges between phases.
- `budgetPolicy` is the agent-owned execution ceiling carried with a resolved `agentRef`. It is the source of truth for agent-specific cost caps: default/max USD budget and optional token ceilings (`maxPromptTokens`, `maxCompletionTokens`, `maxTotalTokens`). A host may provide a stricter outer run envelope, but it must not invent a looser agent cap.

`BudgetPolicy`

- Fields: `defaultBudgetUsd`, `maxBudgetUsd`, `maxPromptTokens`, `maxCompletionTokens`, `maxTotalTokens`, `metadata`.
- This is policy metadata, not spend persistence. Concrete rate tables, quota state, rollups, dashboards, and billing exports remain in `unfurl-foundry`.
- When an agent is invoked from flow through `agentRef`, the effective budget is `min(resolvedAgentBudget, remainingWorkflowRunBudget)` for USD ceilings, plus any token ceilings declared here.

`AgentPhase`

- Fields: `id`, `promptTemplateRef`, `modelRef`, `allowedToolRefs`, `ragQueryRef`, `input`, `outputMapping`, `dependencies`, `maxToolIterations`.
- A phase is one bounded reasoning step: assemble a prompt, optionally retrieve, call the model, optionally call tools, produce structured output.
- `maxToolIterations` bounds the tool-call loop within a phase. It is a control-flow primitive, not a capability name.

`ToolDefinition`

- Fields: `name`, `version`, `description`, `inputSchema`, `outputSchema`, `uses`, `metadata`.
- `uses` is opaque; the substrate never interprets it. The concrete tool handler is an executor resolved through the registry.

`Message` and `PromptTemplate`

- `Message` fields: `role` (`SYSTEM`, `USER`, `ASSISTANT`, `TOOL`), `content`, `toolCallId`, `metadata`.
- `PromptTemplate` fields: `id`, `version`, `segments`, `variables`. Rendering resolves `$.x.y` references and produces an ordered `List<Message>`.

`ModelRequest` and `ModelResponse`

- `ModelRequest` fields: `messages`, `modelRef`, `parameters` (temperature, maxTokens, …), `toolSchemas`, `metadata`.
- `ModelResponse` fields: `message`, `toolCalls`, `finishReason`, `usage` (prompt/completion tokens), `metadata`.
- These are neutral shapes. No provider-specific fields; adapters map to/from concrete SDK types outside the substrate.

`RagQuery`, `RagResult`, `Chunk`

- `RagQuery` fields: `query`, `topK`, `filters`, `collectionRef`, `metadata`.
- `RagResult` fields: `chunks`, `metadata`. `Chunk` fields: `id`, `text`, `score`, `source` (provenance: document id, location, uri), `metadata`.

`EmbeddingRequest` and `EmbeddingResult`

- `EmbeddingRequest` fields: `inputs`, `modelRef`, `metadata`.
- `EmbeddingResult` fields: `vectors`, `usage`, `metadata`.

`AgentRunState`, `AgentPhaseState`, `ToolCall`, `CostAccounting`

- `AgentRunState`: agent run id, agent id/version, status, per-phase states, original agent input, resolved phase inputs, phase outputs, cost accounting, failure details, timestamps, context metadata.
- `AgentPhaseState`: phase id, status, resolved input, messages, model responses, tool calls, output, failure details, timestamps.
- `ToolCall`: call id, tool name, arguments, result or error, timestamps.
- `CostAccounting`: accumulated prompt/completion tokens, per-provider/model token tallies, `estimatedCostUsd`, and attribution dimensions (`tenantId`, `runId`, `agentId`, `phaseId`, `modelRef`, `providerName`, `contractId`, `correlationId`). Accounting only — capture, not enforcement (that is the `CostGuardrail` port) and not aggregation/persistence/billing (that is the reporting layer in `unfurl-foundry`). The attribution dimensions exist so a reporting layer above can roll spend up by tenant, agent, model, or contract without the substrate doing any aggregation.
- Must never require business payloads to be logged; outputs are held in run state for execution only.

Statuses:

- `AgentRunStatus`: `PENDING`, `RUNNING`, `WAITING`, `COMPLETED`, `FAILED`, `CANCELLED`.
- `AgentPhaseStatus`: `PENDING`, `READY`, `RUNNING`, `WAITING`, `COMPLETED`, `FAILED`, `SKIPPED`, `CANCELLED`.

These mirror `WorkflowStatus`/`NodeStatus` deliberately, so flow's durable engine can drive agent phases as ordinary DAG nodes.

### Ports

Ports live in `com.unfurl.foundry.substrate.ports` and all public methods accept `ExecutionContext` (reused from `substrate-ports`).

```java
public interface ModelProvider {
    ModelResponse complete(ModelRequest request, ExecutionContext context);
}

public interface EmbeddingProvider {
    EmbeddingResult embed(EmbeddingRequest request, ExecutionContext context);
}

public interface VectorStore {
    void upsert(String collection, List<Chunk> chunks, ExecutionContext context);
    RagResult query(RagQuery query, ExecutionContext context);
}

public interface ToolExecutor {
    ToolCallResult execute(ToolCallRequest request, ExecutionContext context);
}

public interface ToolRegistry {
    boolean hasTool(String toolName, ExecutionContext context);
    Optional<ToolExecutor> resolveTool(String toolName, ExecutionContext context);
}

public interface RagRetriever {
    RagResult retrieve(RagQuery query, ExecutionContext context);
}

public interface AgentRuntime {
    AgentRunState start(AgentDefinition agent, Map<String, Object> input, ExecutionContext context);
    AgentRunState resume(String runId, Map<String, Object> signal, ExecutionContext context);
    AgentRunState cancel(String runId, ExecutionContext context);
}

public interface ProviderRegistry {
    boolean hasProvider(String name, ProviderKind kind, ExecutionContext context);
    Optional<ModelProvider> resolveModel(String name, ExecutionContext context);
    Optional<EmbeddingProvider> resolveEmbedder(String name, ExecutionContext context);
}

public interface CostGuardrail {
    GuardrailDecision check(CostAccounting accounting, ExecutionContext context);
}

public interface PermissionBridge {
    PermissionDecision check(String toolName, Map<String, Object> arguments, ExecutionContext context);
}
```

Result types are immutable records or sealed interfaces. Do not throw for expected model, tool, retrieval, guardrail, or permission failures; return structured results and let the runner convert them into failed run state. `ProviderRegistry`, `ToolRegistry`, and the credential/loader behind them are *ports*: the per-tenant store, encryption, and concrete adapters are implemented in `unfurl-foundry`.

`CostGuardrail` evaluates the current `CostAccounting` against the resolved `AgentDefinition.budgetPolicy` and any outer execution envelope supplied by the caller. For flow-hosted `agentRef` invocations, flow passes the remaining workflow-run budget in `ExecutionContext.metadata()["outerBudgetRemainingUsd"]`; foundry/foundry-substrate guardrails apply the stricter of the agent cap and that outer value. The substrate shape remains a decision port only: it does not persist quota state or aggregate spend.

### Offers And Composition

Offer fragments and AI invocables live in `com.unfurl.foundry.substrate.offers` and constitute the only place `unfurl-dcp` is imported.

- **Offer fragments** are DCP claim shapes (from `unfurl-dcp`) describing each AI capability a component exposes: operation name (`agent.run`, `tool.call`, `rag.search`, `provider.call`), input/output shape references, and cost-implication metadata. A component publishes these as part of its claim; fabric negotiates them; the broker registers the accepted ones.
- **AI invocables** implement the substrate `ContractInvocable`:

```java
public final class AgentInvocation implements ContractInvocable {
    public String contractId() { ... }
    public String contractVersion() { ... }
    public ContractInvocationResult invoke(ContractInvocation invocation, ExecutionContext context) {
        // maps the frozen-contract invocation onto AgentRuntime.start(...) and back
    }
}
```

`ToolInvocation` and `RagInvocation` follow the same shape. These are what the `unfurl-dcp` broker registers into a host `CapabilityRegistry` on accept; they translate a frozen-contract invocation into a call on the corresponding port (`AgentRuntime`, `ToolExecutor`, `RagRetriever`) and map the structured result back. The substrate provides the invocables and the shapes; it never decides whether the contract should exist.

### Events

AI events live in `com.unfurl.foundry.substrate.events`, reusing the substrate `Event` envelope (and its `correlationId`/`integrityHash` metadata fields) with AI event types:

- `AGENT_STARTED`, `AGENT_COMPLETED`, `AGENT_FAILED`, `AGENT_CANCELLED`
- `PHASE_STARTED`, `PHASE_COMPLETED`, `PHASE_FAILED`, `PHASE_WAITING`
- `MODEL_INVOKED`, `TOKENS_CONSUMED`
- `TOOL_CALLED`, `TOOL_COMPLETED`, `TOOL_FAILED`
- `RAG_RETRIEVED`
- `GUARDRAIL_TRIPPED`

Event payloads are metadata-first: token counts, tool names, model refs, phase ids — never full prompts, model outputs, or retrieved chunk text by default.

---

## Execution Flow

`EmbeddedAgentRuntime` is sequential and in-process only — the AI peer of `EmbeddedExecutionEngine`.

Bootstrapping dependencies:

- `AgentRunStore`, usually in-memory.
- `EventSink`, usually `NoopEventSink` (reused from substrate).
- `ProviderRegistry` / `ModelProvider`, usually a no-op or echo provider in tests.
- `ToolRegistry`, `RagRetriever`, usually fakes in tests.
- `agentRef`/`toolRef` resolver.
- `CostGuardrail`, `PermissionBridge`; the embedded runner defaults to `BudgetPolicyCostGuardrail` for agent-owned budget ceilings and an allow-all permission bridge.
- `PolicyEvaluator`, usually `DefaultAllowAllPolicyEvaluator` (reused from substrate).

Start flow:

1. Validate policy action `agent.start`.
2. Validate the agent definition: unique phase ids, edge endpoints, no cycles outside conditional routing, references, conditions, tool refs, and model ref availability through `ProviderRegistry`.
3. Create `AgentRunState` with all phases `PENDING`, entry phases `READY`, run `RUNNING`.
4. Persist and emit `AGENT_STARTED`.
5. Loop while ready phases exist:
   - Pick the next ready phase by deterministic topological order.
   - **Resolve the phase input from agent input and completed upstream phase outputs (the resolver is wired here — see Gap A in the substrate review).**
   - Mark phase `RUNNING`, persist, emit `PHASE_STARTED`.
   - Assemble the prompt via `foundry-substrate-prompt` (rendering resolves references into `List<Message>`).
   - If the phase declares a RAG query, call `RagRetriever`, emit `RAG_RETRIEVED`, and fold results into the prompt.
   - Check `CostGuardrail`; the runner attaches the resolved `AgentDefinition.budgetPolicy` to `ExecutionContext` before the check. On a tripped budget, emit `GUARDRAIL_TRIPPED` and fail the phase.
   - Call `ModelProvider.complete`, emit `MODEL_INVOKED` and `TOKENS_CONSUMED`, update `CostAccounting` with prompt/completion tokens, provider/model attribution, and optional `estimatedCostUsd` / `costUsd` response metadata.
   - While the response contains tool calls and `maxToolIterations` is not exceeded: check `PermissionBridge`, resolve the tool via `ToolRegistry`, execute, emit `TOOL_CALLED`/`TOOL_COMPLETED`, append the tool result message, and call the model again.
   - On success, store the phase output, mark `COMPLETED`, persist, emit `PHASE_COMPLETED`.
   - On a waiting result (e.g. a tool requires human approval), store the wait, mark phase and run `WAITING`, emit `PHASE_WAITING`, and stop.
   - Evaluate outgoing conditional edges; mark non-matching branch phases `SKIPPED`; mark unblocked phases `READY`.
   - On failure, mark phase and run `FAILED`, persist, emit failure events, and stop.
6. If all reachable phases are `COMPLETED` or `SKIPPED`, mark the run `COMPLETED`, persist, emit `AGENT_COMPLETED`.

Resume flow:

- Load the run and find a `WAITING` phase whose wait correlation matches the signal.
- Apply the signal as the resolved tool/phase result, mark the phase `READY`/`COMPLETED`, and continue the sequential loop.
- Durable, distributed, and streaming resume behavior belongs to `unfurl-foundry`; the substrate provides only the in-memory wait/resume floor. (The substrate review's Gap C is pre-empted here: `resume()` actually re-runs the loop.)

Cancellation:

- Validate `agent.cancel`. Mark unfinished phases `CANCELLED`, run `CANCELLED`, persist, emit `AGENT_CANCELLED`.

In-memory `AgentRunStore`:

- Uses `ConcurrentHashMap<String, AgentRunState>`, returns defensive snapshots, does not persist to disk, and does not coordinate across processes.

Constraints:

- Phase topology is static and validated before execution.
- Execution is sequential only; no parallel phases, durability, or retries.
- The tool-call loop within a phase is bounded by `maxToolIterations`.
- No concrete model/embedding/vector calls happen in the substrate; they go through ports whose concrete bindings are supplied by foundry/adapters.

---

## Composition Flow

Composition is the runtime side of dynamic capability addition, viewed from this layer's responsibility.

`unfurl-foundry-substrate` exposes, in `foundry-substrate-offers`:

- AI capability **offer fragments** that a component publishes in its DCP claim.
- AI **`ContractInvocable` implementations** (`AgentInvocation`, `ToolInvocation`, `RagInvocation`) that translate a frozen-contract invocation onto the corresponding port.

The `unfurl-dcp` broker (a depended-upon collaborator, not part of this layer) performs the deterministic accept/reject against the fabric-frozen contract and, on accept, registers these invocables into the host `CapabilityRegistry`:

```text
present claim (offers)                      [component]
        v
deterministic frozen-contract lookup        [unfurl-dcp broker]
   accept -> register offers                       |
        v                                          |
host CapabilityRegistry: uses -> ContractInvocable  <-- AgentInvocation/ToolInvocation/RagInvocation (this layer)
        v
host engine: resolveExecutor("agent.run") -> invoke over frozen contract (in-process via substrate
                                              InProcessContractDispatcher, or transport when separate)
```

Broker internals — claim matching, disposition, registration — are `unfurl-dcp`'s. This layer's contract with the broker is: *given an accepted frozen contract, here is the invocable that fulfils it.* Network DCP transport is not implemented here; the in-process path reuses the substrate dispatcher.

---

## Validation And Error Model

Use layered validation:

1. Jackson deserialization for shape.
2. Jakarta Validation for required fields and field-level constraints.
3. Explicit validation services for agent topology, reference validity, condition validity, tool-ref availability, model-ref availability, and offer-fragment well-formedness.

Error model:

- Expected failures return structured error records.
- Top-level error categories: `VALIDATION`, `POLICY_DENIED`, `RESOLUTION`, `CONDITION`, `MODEL_FAILED`, `TOOL_FAILED`, `RETRIEVAL_FAILED`, `PROVIDER_MISSING`, `TOOL_MISSING`, `GUARDRAIL_TRIPPED`, `PERMISSION_DENIED`, `WAIT_NOT_FOUND`, `COMPOSITION`, `RUN_NOT_FOUND`, `CANCELLED`.
- Errors include location fields where applicable: agent id, phase id, edge id, tool name, model ref, reference expression, condition expression, run id, wait id, iteration index.
- Runtime exceptions are reserved for programmer errors and are wrapped at runner boundaries into structured run failures.

Serialization:

- Public models (agent, tool, prompt, offer fragment) must round-trip JSON and YAML stably.
- Wire-facing field names use lower camel case unless an upstream DCP/shared artifact requires otherwise.

---

## Enterprise Trust, Audit, And Compliance Considerations

The same boundary-not-integration approach as the deterministic substrate.

Contract trust:

- DCP owns frozen contract schema, the runtime broker, and signature verification; fabric owns signing; products own boot-time verification policy.
- The substrate provides offer fragments, `ContractInvocable` impls, `correlationId`, and optional `integrityHash` metadata so verified AI contracts can be invoked safely in-process.

Provider credentials and licensing:

- Per-tenant credential loading, encryption, and enforcement live in `unfurl-foundry`.
- The substrate must not start network calls, background checks, or vendor callbacks. Provider/Tool registries are injectable so products enforce local constraints before a model/tool call.

Audit:

- AI events reuse the substrate `Event` envelope and are metadata-first: token counts, tool names, model refs, phase ids — never full prompts/outputs/chunks by default.

Cost and metering:

- The substrate captures cost (`CostAccounting`, `TOKENS_CONSUMED`/`MODEL_INVOKED`/`TOOL_CALLED` events, `MetricsProvider` cost metrics) with full attribution metadata; it does not aggregate, persist, or bill.
- Reporting, rollup, quota/budget state, and usage/billing export live in `unfurl-foundry`, run in the customer's perimeter, and phone home to nothing; any vendor-side billing is a separate, consented export.
- `AgentDefinition.budgetPolicy` carries the agent-owned default/max budget and token ceilings that travel with `agentRef` resolution. `CostGuardrail` returns a per-call decision and may emit `GUARDRAIL_TRIPPED`; budgets and rate limits are enforced above, never persisted in the substrate.
- When a host supplies an outer run envelope, the effective agent cap is the lower of the resolved agent budget and the remaining outer budget. This lets flow remain deterministic and AI-free while foundry/foundry-substrate own the agent cost semantics.

Telemetry and auth:

- Trace/metrics go through substrate ports only; W3C trace context is neutral metadata in `ExecutionContext`. The substrate validates no tokens and fetches no JWKS; products translate identity into `ExecutionContext`.

No-phone-home:

- No class in `unfurl-foundry-substrate` may open sockets, use HTTP/model SDK clients, call Unfurl services, fetch remote keys, or emit telemetry directly. ArchUnit enforces the dependency side.

---

## Testing And Architecture Enforcement

Unit tests:

- Domain serialization and validation (agent, tool, prompt, offer fragment).
- `agentRef`/`toolRef` resolver parsing and missing-reference errors.
- Prompt assembly and reference resolution into ordered messages.
- Tool-call loop bounding by `maxToolIterations`.
- Cost accounting accumulation and guardrail decision plumbing.
- Runtime success, model/tool/retrieval failure, missing provider/tool, conditional branch skip, wait and resume, cancellation, context propagation, event sequence.
- Offer-fragment well-formedness and `ContractInvocable` mapping (invocation in, port call, structured result out).

Property tests with jqwik:

- Reference resolution determinism for generated nested maps.
- Conditional-edge routing determinism for generated phase DAGs.
- Topological phase-scheduler determinism.
- Cost accounting associativity over generated usage sequences.

Architecture tests with ArchUnit:

- No model, embedding, vector, or HTTP SDK usage anywhere.
- No `unfurl-flow`, `unfurl-foundry`, or `unfurl-fabric` imports.
- `unfurl-dcp` imported only by `foundry-substrate-offers`.
- No web framework, DB, queue/cache, cloud, auth, or observability SDK usage.
- No production package depends on `foundry-substrate-testing`.
- Module dependency graph matches the allowed edges in this LLD.
- `foundry-substrate-domain`, `-ports`, `-prompt`, `-tools`, `-rag`, `-resolver`, `-offers` do not depend on `foundry-substrate-engine`.
- Only `foundry-substrate-testing` depends on test fixtures.

Enterprise tests:

- No-op providers/sinks perform no I/O.
- Events omit prompts, model outputs, and chunk text unless explicitly supplied by caller metadata.
- `correlationId` propagates from `ExecutionContext` to AI events and contract invocations.
- The composition path calls only `ContractInvocable`, never provider internals.

---

## Implementation Sequence

1. Create the Maven multi-module skeleton, centralized dependency/plugin management, JDK 21 compiler settings, coordinated publishing version, Maven Enforcer rules, ArchUnit baseline, and the dependency on `unfurl-substrate` (+ `unfurl-dcp` for the `offers` module only).
2. Implement domain models (agent, phase, tool, message, prompt, model, rag, embedding, run state), Jackson configuration, and `AgentDefinitionValidator`.
3. Implement AI ports, structured result/error types, and no-op/fake defaults.
4. Implement prompt/message assembly with reference resolution.
5. Implement tool invocation shapes and `DefaultToolRegistry`.
6. Implement RAG query/result/provenance shapes and the retriever port surface.
7. Implement `agentRef`/`toolRef` resolution with unit/property tests.
8. Implement offer fragments and `ContractInvocable` impls (`AgentInvocation`, `ToolInvocation`, `RagInvocation`) against `unfurl-dcp` claim/contract types.
9. Implement the in-memory `AgentRunStore` and `EmbeddedAgentRuntime`, with the resolver wired into phase-input and condition paths and a real `resume()`.
10. Implement AI event schema and metadata-safe event builders.
11. Implement downstream testing fixtures (echo provider, static embedder, in-memory vector store, recording tool executor).
12. Complete coverage, architecture, and enterprise guardrail tests before flow and foundry consume the library.
