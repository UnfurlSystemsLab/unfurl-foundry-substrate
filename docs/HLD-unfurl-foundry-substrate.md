# High-Level Design: `unfurl-foundry-substrate`

**Document status:** Architecture HLD for the AI-side substrate library family.
**Audience:** Engineers and architects working on `unfurl-foundry-substrate`, `unfurl-foundry`, `unfurl-flow`, and `unfurl-dcp`.
**Primary sources:**

- `00-product-architecture.md`
- `REPO-unfurl-substrate-build-spec.md`, `LLD-unfurl-substrate-java.md`
- `HLD-A-portfolio.md`, `HLD-B-intelligent-components-v3-GPT.md` (Intelligent Components; Provider Registry, RAG Pipeline, Tool Registry)
- `HLD-C-dcp-v0.2-internal.md`, `HLD-C2-dcp-schema-spec-updated.md` (DCP three planes)
- `REPO-unfurl-flow-build-spec.md`, `ADR-001-platform-language-scope.md`

---

## Purpose And Sources

`unfurl-foundry-substrate` is the AI-side counterpart of `unfurl-substrate`. Where `unfurl-substrate` owns the *deterministic* workflow/execution shapes and ports, `unfurl-foundry-substrate` owns the *probabilistic* shapes and ports — agents, tools, models, embeddings, retrieval — as a thin JDK 21 Maven multi-module library with no concrete model SDKs and no deployable runtime of its own.

It exists so two products can share stable AI contract types without importing each other's internals, and so those products can be co-packaged into one JVM and still communicate through contract-shaped interfaces:

- **`unfurl-flow` depends on it** to gain AI capability without taking on a single model SDK. Flow registers the AI executors this layer defines; the actual reasoning runs behind ports or over a frozen DCP contract.
- **`unfurl-foundry` builds on it** — adding durable/distributed execution, concrete provider adapters, a vector store, per-tenant registries, and a server — to stand up a standalone AI-agent orchestration service.

This document mirrors the substrate's design philosophy and voice deliberately. The whole point is that the AI side is *the same kind of layer* as the deterministic side: thin, auditable, ports-first, and incapable of phoning home.

`00-product-architecture.md` is the topology source of truth. Nothing in this HLD moves Fabric's design-time intelligence, DCP claim/contract schemas, or concrete provider behavior into the substrate. Those remain owned by `unfurl-fabric`, `unfurl-dcp`, and the adapters/products above.

---

## What This Layer Is

`unfurl-foundry-substrate` has two jobs, both load-bearing for the residency wedge:

1. **Logical independence:** the AI model and ports that let flow and foundry remain peers — agent/tool/model/rag/embedding shapes, AI capability *offers*, and execution-state vocabulary shared without internal coupling.
2. **Physical collapsibility:** the AI capabilities a host exposes through `CapabilityRegistry` and invokes through `ContractInvocable`, so an agent in foundry and a durable DAG in flow can be co-packaged into one deployable and still talk through contract-shaped calls.

The substrate ships only a **minimal embedded agent runner** and a **minimal embedded agent harness**: enough to run a multi-phase agent in-process with injected ports, an in-memory run state, and no-op providers, and enough to repeat bounded agent turns until the agent returns a terminal, clarification, gap, or continue envelope. Durable execution, distributed execution, streaming, retries, concrete model/embedding/vector calls, per-tenant credential stores, and model hosting belong to `unfurl-foundry`, not here.

If an implementation starts adding a model SDK, an HTTP client, a vector-DB driver, a credential store, durable persistence, or design-time negotiation intelligence, stop. That belongs above the substrate.

---

## Owns / Does Not Own

`unfurl-foundry-substrate` owns:

- portable agent definition shape (a multi-phase agent as a DAG of phases with conditional edges)
- tool, prompt/message, model-request/response, RAG query/result, and embedding shapes
- `FoundrySubstrateCodec` for stable JSON/YAML round trips of public model records
- AI execution-state shape (agent run, phase state, tool-call records, cost accounting metadata)
- provider-neutral model-turn outcomes and structured tool-failure vocabulary
- portable context-selection, terminal-output, semantic-validation, tool-interceptor, and agent-delegation contracts
- AI ports: model provider, embedding provider, vector store, tool executor/registry, RAG retriever, agent runtime, agent harness runtime, provider registry, cost-guardrail, permission bridge, tool-call interceptor, semantic validator, and agent delegate
- AI capability **offers** expressed as DCP claim fragments (`agent.run`, `tool.call`, `rag.search`, `provider.call`)
- recursive DCP projection bridge claims for agents, phases, skills, tools, prompts, RAG, models, and Flow workflow references; these are visualization/inspection claims, not runtime execution decisions
- AI `ContractInvocable` implementations that expose those offers over a frozen contract
- AI `NodeExecutor` adapters for `agent.run`, `tool.call`, `rag.search`, and `provider.call`, so flow can register AI capability in its normal substrate `CapabilityRegistry`
- `agentRef` / `toolRef` reference resolution
- AI event schema
- cost-accounting and metering shapes — per-run token/cost capture and metering-grade event metadata — `BudgetPolicy`, and the `CostGuardrail` decision port with a no-I/O `BudgetPolicyCostGuardrail` default, without aggregation, persistence, quota state, or billing
- a minimal embedded agent runner and harness runner with in-memory state and no-op providers

`unfurl-foundry-substrate` does not own:

- concrete model/embedding/vector SDKs (Anthropic, OpenAI, Azure, Ollama, Voyage, pgvector, …) — adapters
- per-tenant credential storage, encryption, or rotation — `unfurl-foundry`
- cost aggregation/rollup, usage persistence, dashboards, quota/budget state, and billing/usage export — `unfurl-foundry`
- durable, distributed, streaming, or parallel agent execution; retries; work queues — `unfurl-foundry`
- LoRA training, fine-tuning, or model hosting — training pipeline / `unfurl-foundry`
- the **runtime composition broker** (claim → disposition → contract → capability exposure) — `unfurl-dcp`
- **design-time negotiation intelligence** (accept/reject/refusal reasoning) — `unfurl-fabric`
- DCP claim/contract/disposition **schemas** — `unfurl-dcp`
- product servers, APIs, containers, databases, cloud SDKs, or auth implementations — `unfurl-foundry`
- prompt content, model selection policy, or any runtime model reasoning of Unfurl's own

---

## Position In The Portfolio

```text
unfurl-substrate           domain, ports (CapabilityRegistry, NodeExecutor), composition-api
                           (ContractInvocable), in-process dispatcher, embedded engine
unfurl-dcp                 Claim / CompositionContract / Disposition schemas
                           + RUNTIME composition broker (deterministic accept/reject + capability exposure)
unfurl-fabric              DESIGN-TIME negotiation intelligence -> frozen composition contracts
unfurl-foundry-substrate   AI domain shapes + AI ports + AI capability OFFERS (DCP-shaped)
                           + AI NodeExecutor/ContractInvocable impls + minimal EmbeddedAgentRuntime
                           + minimal EmbeddedAgentHarnessRuntime
unfurl-flow                HOST: deterministic orchestrator; registers AI executors to gain AI capability
unfurl-foundry             HOST + RUNTIME: adds durable/distributed runtime, provider adapters,
                           vector store, per-tenant registries, server
```

Dependency direction: `unfurl-foundry-substrate -> unfurl-substrate` (reuses domain, ports, composition-api, resolver) and `-> unfurl-dcp` (only for claim/contract types, and only in the `offers` module). It never depends on `unfurl-flow`, `unfurl-foundry`, or `unfurl-fabric`. Hosts depend on it; it depends on no host.

The deterministic substrate and the AI substrate are **peers** — two thin layers of the same kind. A host (flow or foundry) composes whichever it needs.

---

### Agent Harness Boundary

An agent harness is the bounded control loop that repeatedly invokes an agent, observes the structured terminal phase output, and decides whether the agent is done, needs clarification, has found a gap, or should continue with new input. It is deliberately one level above `AgentDefinition`: the agent DAG still owns phase topology and tool use inside a turn, while the harness owns repeated turns around that agent.

The substrate owns the portable harness model and `AgentHarnessRuntime` port so embedded hosts can use the same construct without importing the Foundry product. The embedded substrate harness is sequential, in-memory, and deterministic. It recognizes the following terminal output convention:

- `kind: continue` with `nextInput` as an object: run another bounded turn.
- `kind: clarify` or a non-empty `questions` list: stop in `WAITING_FOR_USER`.
- `kind: gap` or a non-empty `unmet` list: stop in `GAP`.
- any other completed agent output: stop in `COMPLETED`.

Foundry owns durable/persisted harness execution, server APIs, recovery, queueing, streaming, and deployment wiring. Flow may still use Foundry through DCP `agent.run` or a future coarse-grained harness binding; Flow does not need to model the inner agentic loop as a chatty DAG.

### Graph Core, Agent Interface

Foundry does not choose between a graph representation and an Anthropic/Codex-style agent interface. The two forms serve different boundaries and are deliberately layered:

- The **agent graph is the canonical intermediate representation**. `AgentDefinition` remains a versioned, serializable, inspectable DAG of bounded phases and conditional edges. It is the source of truth for validation, provenance, Studio visualization, and deterministic routing between declared reasoning phases.
- The **agent harness is the execution interface**. It exposes the familiar model/tool/observation loop, repeated turns, clarification, gap, escalation, cancellation, and resume without exposing every model turn as a workflow node.
- The **tool loop is dynamic but bounded**. Within one phase the model may request a permitted tool, observe its result, and continue until a provider-neutral terminal outcome or `maxToolIterations` is reached.
- **Deterministic policy surrounds probabilistic choice**. Permission, prerequisite, approval, budget, schema, and semantic-validation decisions execute through ports before or after model/tool operations; prompt wording is not an enforcement mechanism.

The graph is therefore not a visual encoding of a transcript, and the harness is not a second agent-definition format. A deployment may present a chat-like or coding-agent interface while still executing a pinned graph underneath.

### `agentRef` Invocation Boundary

Flow invokes an agent as one coarse capability node:

```yaml
- id: investigate
  uses: agent.run
  config:
    agentRef: support-coordinator@1.2.0
    input:
      request: $.workflow.input.request
      caseFacts: $.workflow.state.caseFacts
```

`agentRef` is an `id@version` reference resolved before execution. The resolved view is immutable and provenance-annotated and carries the agent definition digest, input and terminal-output schema refs, harness policy, referenced tools/skills/prompts/models/RAG sources, effective governance constraints, and DCP binding identity. Flow schedules the `agent.run` node and persists its coarse result; Foundry schedules the internal phases, model turns, tools, and child-agent calls.

The contract returns a provider-neutral terminal envelope with a status such as `COMPLETED`, `WAITING_FOR_USER`, `ESCALATED`, `GAP`, `FAILED`, or `CANCELLED`, plus structured output, questions, handoff, confidence, provenance, error, and metering fields as applicable. Provider-native stop reasons and message formats never cross this boundary.

### Context, Validation, And Delegation

Agent execution must be explainable from explicit inputs rather than hidden transcript state:

- `ContextPolicy` describes history selection, pinned facts, summary references, tool-result retention, provenance retention, and token allocation. The substrate owns the shape and selection port; Foundry owns summarization, persistence, and concrete stores.
- Output schemas guarantee shape. A `SemanticValidator` port checks domain meaning and may return specific correction feedback. Correction retries are bounded by policy; exhaustion produces escalation or a structured failure.
- `ToolCallInterceptor` is a Chain of Responsibility around every tool call. Before-call interceptors may deny, require approval, or attach normalized arguments; after-call interceptors may normalize, redact, or annotate results. They cannot widen permissions or bypass a frozen DCP contract.
- `AgentDelegate` invokes a pinned child `agentRef` with an explicit context projection, expected output schema, and stricter-or-equal tool/budget/permission envelope. Children do not inherit an implicit parent transcript or undeclared tools. Independent child calls may run concurrently only in a host runtime that provides that facility.

The substrate supplies only neutral contracts and a sequential reference implementation. Durable checkpoints, concurrent delegation, approval queues, retry scheduling, and human-review work queues remain Foundry responsibilities.

### MCP Adapter Boundary

MCP is an integration protocol, not a substrate primitive. An MCP server is bound by a Foundry-owned adapter that projects MCP tools and resources into neutral `ToolDefinition`/`ToolExecutor` and context-resource ports. Process management, HTTP/stdio transport, credentials, discovery caching, and server lifecycle stay in Foundry or an adapter repository. The substrate never imports an MCP SDK or opens a transport. When an imported capability crosses components, its use remains governed by a DCP claim and frozen contract.

---

## DCP-Driven Dynamic Composition (The Centerpiece)

The reason this layer exists in its particular shape is to support **adding AI components to a host dynamically through DCP** — acceptance, rejection, and handshaking done through the protocol — and to have an accepted component's capabilities **surface on the host**. The lifecycle spans four planes-of-responsibility; `unfurl-foundry-substrate` owns only two pieces of it, and is explicit about where the rest lives.

### Plane 1 — Description (the claim)

An AI component publishes a DCP **claim** whose `offers` are its capabilities: an agent component offers `agent.run`, a retrieval component offers `rag.search`, a tool component offers `tool.call`, a model gateway offers `provider.call`. `unfurl-foundry-substrate` provides these **offer fragments** — the DCP-shaped declarations of what each AI capability exposes (operation names, input/output shape references, cost implications). The claim schema itself is owned by `unfurl-dcp`; this layer only fills in the AI-specific fragments.

Those AI claims also declare DCP fault vocabulary for the AI capabilities they expose. A provider, tool, RAG, skill, or agent fault is not modeled as a substrate-specific type here; it is expressed through the DCP claim `faults` section and evaluated by `unfurl-dcp`'s deterministic propagation gate. Metadata-only or passive library components still declare an explicit empty fault policy so catalog consumers can distinguish "no declared faults" from "fault section missing."

### Plane 2 — Negotiation (design-time, fabric)

When a component is being composed into a host, **`unfurl-fabric` negotiates** — at authoring/design time — whether the host accepts the component, partially accepts it, or refuses it (with a redirection saying what kind of component *should* own the refused concern). All accept/reject *reasoning* happens here, and the result is **frozen into a composition contract**. This plane is intelligent; it runs in the authoring environment, never in the customer's perimeter. It is shown here for context only — it is out of scope for `unfurl-foundry-substrate`.

### Plane 3 + dynamic add (runtime, the `unfurl-dcp` broker)

At runtime a host presents a claim to the **`unfurl-dcp` composition broker**. The broker performs a **purely deterministic** step: it looks up the matching fabric-frozen contract.

- A frozen contract exists and the version matches → **accept**.
- No matching contract → **refuse**, returning fabric's pre-computed redirection.

There is no model, no reasoning, and nothing phones home. "Dynamic" here means a component can be added or removed at runtime and the host's capability surface changes accordingly — but every accept/reject *decision* was already frozen at design time. This is the residency wedge in one sentence: **negotiate once, at design time; at runtime, look up and invoke.**

### Plane 3 — Capability exposure and invocation

On **accept**, the broker registers the guest's `offers` into the host's `CapabilityRegistry`: each offered capability becomes a `uses` → executor entry backed by a `ContractInvocable` bound to the frozen contract. The host engine can now resolve and invoke the new capability — in-process via the substrate dispatcher when co-packaged, or over the contract's transport when separate. Remove the component and its capabilities disappear from the registry.

`unfurl-foundry-substrate`'s role in this final step is concrete and bounded: it supplies the **AI `ContractInvocable` implementations** (`agent.run`, `tool.call`, `rag.search`, `provider.call`) that the broker registers, and the agent/tool/model/rag **shapes** those invocations carry. It does not own the registration mechanism (that is the broker's) nor the decision (that is fabric's, frozen).

For direct host registration, the ports module also supplies the matching substrate `NodeExecutor` adapters: `AgentRuntimeNodeExecutor`, `ToolExecutorNodeExecutor`, `RagRetrieverNodeExecutor`, and `ModelProviderNodeExecutor`. These let flow treat AI capabilities as ordinary `uses` targets while foundry-substrate keeps the model/tool/rag/agent semantics behind ports.

```text
[component]            present claim (offers: agent.run, rag.search, ...)
     |                          v
[unfurl-dcp broker]   deterministic lookup of fabric-frozen contract
     |                  accept (match) | refuse (no match, + redirection)
     v  (on accept)
[host CapabilityRegistry]  uses -> ContractInvocable(frozen contract)   <-- foundry-substrate impls
     |
[host engine]         resolveExecutor("agent.run") -> invoke over contract (in-process / transport)
```

---

## Runtime Capability Loading

Hosts load capabilities in two complementary ways, and both bottom out in the substrate `CapabilityRegistry`:

- **Statically, by profile (flow's pattern).** Flow's runtime profile declares the engine, state store, event sink, and enabled components; the profile loader constructs and registers them and fails at load if wiring is missing. This is how flow's deterministic components are loaded today, and how it would register the AI executors this layer defines.
- **Dynamically, by DCP composition (the new pattern above).** The `unfurl-dcp` broker registers an accepted component's offers at runtime, so capabilities can appear and disappear without redeploying.

Foundry adds a third, data-driven flavor on top of the same registry: **per-tenant Provider and Tool registries**. Foundry registers LLM/embedder providers per tenant (with encrypted credentials and concrete provider adapters) and loads tool definitions, then resolves them by logical name using `ExecutionContext`. `unfurl-foundry-substrate` provides the **registry ports** (`ProviderRegistry`, `ToolRegistry`) and the tenant-scoped `resolve(name, kind, context)` shape; the encrypted-credential store, YAML loading, and concrete adapters live in foundry. The substrate owns the port; the product owns the loader.

Catalog claims for substrate components must use the neutral port names when they depend on provider implementations: `model-provider`, `embedding-provider`, and `vector-store`. Framework-specific names such as Spring AI are adapter-internal implementation details. A Spring AI adapter may declare host-owned Spring bean needs as runtime leaf bindings, but its offers still satisfy the neutral provider ports so recursive DCP closure can select it like any other provider adapter.

In every case the concrete implementation behind a logical name is a **frozen, negotiated binding** — resolved at runtime, never discovered or re-negotiated in the hot path.

---

## Recursive DCP Projection Bridge

`foundry-substrate-offers` owns the Flow/Foundry domain-to-DCP projection bridge used by Fabric Studio semantic zoom. `FoundryClaimProjector` synthesizes DCP claims from `AgentDefinition` and related declarations. The graph is phase-aware:

```text
Foundry -> Agent -> Phase -> {Prompt, Model, RAG, Tool, Skill}
Skill -> {Prompt, Model, RAG, Tool}
```

`FlowClaimProjector` synthesizes DCP claims from `WorkflowDefinition` and bridges workflow node `uses: agent:<id>` to the shared `urn:unfurl:foundry:agent:<id>` URI. Merging the Flow and Foundry claim maps yields a single recursive graph that Fabric can render through its generic `dynamic-dcp/project` adapter without importing Flow or Foundry domain types.

The bridge emits only structural DCP inspection claims with `metadata.extensions.contains`; it does not negotiate, accept, deploy, invoke, or introspect provider secrets.

---

## Cost, Metering, And Reporting

Cost is split the same way audit and telemetry are: **the substrate captures, the product reports.** A thin, SDK-free, no-I/O layer cannot own aggregation, persistence, or billing — but it must produce metering-grade signal that a reporting layer above can trust.

`unfurl-foundry-substrate` owns the **capture** half:

- A per-run `CostAccounting` shape that accumulates prompt/completion tokens and per-provider/model tallies as phases, model calls, and tool calls execute.
- Typed model attribution on `ModelResponse`: `providerName` and `estimatedCostUsd`, with metadata-key fallback for older providers.
- Metering events — `MODEL_INVOKED`, `TOKENS_CONSUMED`, `TOOL_CALLED` — emitted metadata-first through the existing `EventSink`, and cost metrics through the existing `MetricsProvider` port. No new sink type is introduced.
- Sufficient **attribution metadata** on accounting and events for any rollup above: `tenantId`, `runId`, `agentId`, `phaseId`, `modelRef`, `providerName`, `contractId`, `correlationId`.
- The `CostGuardrail` port — a yes/no decision the runner consults before a model call — emitting `GUARDRAIL_TRIPPED`. It is a decision, not enforcement state.
- `BudgetPolicy` on `AgentDefinition` and a no-I/O `BudgetPolicyCostGuardrail` default. `CostGuardrailContext` uses `ExecutionContext.metadata` keys `agentBudgetPolicy` and `outerBudgetRemainingUsd` so a host can pass the stricter outer run envelope without giving the substrate quota state.

`unfurl-foundry` owns the **reporting** half: aggregation and rollup, usage persistence, per-tenant dashboards, quota/budget state, and usage/billing export. Two boundary rules:

- **Reporting stays in-perimeter.** Cost rollups run in the customer's deployment like everything else; any vendor-side billing is a separate, consented export, never a runtime phone-home (offline-licensing rule).
- **Cost is contract-attributable.** DCP offers carry `cost_implications` (required when metered) and the frozen contract pins them, so reporting can attribute spend to the contract/offer, not just the model call. The substrate's offer fragments surface that field.

The same capture-not-persist principle covers the related cross-cutting concerns: **quotas, budgets, and rate-limiting** are stateful enforcement concerns owned by foundry, exposed to the substrate only as ports (`CostGuardrail`, `PermissionBridge`) and the pure `BudgetPolicyCostGuardrail` default. The substrate signals and makes per-call decisions; it never persists quota state, stores rate tables, or aggregates.

---

## How Flow Gains AI Capability

Flow is the deterministic orchestrator and has no AI dependency of its own. It gains AI capability by:

1. Depending on `unfurl-foundry-substrate` and registering its AI executors into flow's `CapabilityRegistry` (via profile or via the DCP broker).
2. Resolving `agentRef` / `toolRef` references at load time to agent/tool definitions from this layer's domain types — flow provides the resolution mechanism; the agent semantics are foundry's.
3. Dispatching one coarse `uses: agent.run` node for a pinned `agentRef`. Foundry owns the agent's internal phase graph, bounded tool loop, continuation context, child-agent calls, and harness state. Flow owns the surrounding deterministic workflow and may durably execute a separately declared delegated workflow through `workflow.execute`.

An internal agent phase is not automatically a Flow node. Promote work to a Flow workflow only when it is a business-visible deterministic unit that needs Flow-owned durability, scheduling, compensation, or cross-service coordination. This avoids turning every model/tool turn into a chatty distributed DAG while keeping genuinely durable work explicit.

A workflow with no agent node still runs entirely within flow with zero model involvement. The AI capability is additive and contract-bounded.

---

## How Foundry + Foundry-Substrate Become A Standalone Service

`unfurl-foundry-substrate` provides the agent domain, AI ports, offers, AI `ContractInvocable` impls, and the minimal embedded agent runner. `unfurl-foundry` turns that into a deployable product by adding:

- durable and distributed agent execution (checkpoint-based, not replay), retries, and work queues
- concrete provider adapters (Anthropic, OpenAI, Azure, Ollama, Groq, HuggingFace, Voyage, …) behind the `ModelProvider`/`EmbeddingProvider` ports
- a concrete vector store behind the `VectorStore` port
- per-tenant Provider/Tool registries with encrypted credentials and TTL caching
- a server, multi-agent composition, and cost-guardrail enforcement

Agent-level budget policy travels with the agent contract itself: a resolved `agentRef` points to an `AgentDefinition` whose budget policy declares the agent-owned default/max spend and token ceilings. A host such as flow may supply a stricter outer workflow-run envelope, but the effective agent cap is the lower of the resolved agent budget and the remaining outer budget; flow never invents agent cost semantics.

Together they are a standalone AI-agent orchestration service — and because every model call goes through a port whose concrete binding the customer configures, the service runs entirely in the customer's perimeter.

---

## Enterprise Posture

The design preserves the same enterprise posture as the deterministic substrate:

- **No phone-home.** No class in `unfurl-foundry-substrate` opens sockets, uses an HTTP client, calls Unfurl services, fetches remote keys, or emits telemetry directly. ArchUnit enforces the dependency side.
- **The residency-wedge nuance, stated plainly.** "No runtime AI reasoning" means no *Unfurl* design-time intelligence in the perimeter. Plane-3 invocation of the **customer's own** configured models at runtime is allowed and expected — but always behind a `ModelProvider` port whose concrete adapter the customer supplies. The substrate layer itself never imports a model SDK and never selects or hosts a model.
- **All negotiation is design-time.** Accept/reject reasoning lives in fabric and is frozen; the runtime handshake is deterministic lookup.
- **Correlation everywhere.** `correlationId` propagates from `ExecutionContext` through agent events and contract invocations, exactly as on the deterministic side.
- **Trust and audit by boundary, not integration.** Contract signing/verification and credential ownership live in DCP/fabric/foundry; the substrate carries the neutral metadata fields those layers need and computes no cryptographic proofs itself.
