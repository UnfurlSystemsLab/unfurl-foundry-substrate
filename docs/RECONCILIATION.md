# Reconciliation: `unfurl-foundry-substrate` Against The Existing Corpus

**Document status:** Reconciliation note. Maps the foundry-substrate HLD/LLD onto the existing design corpus and flags the one genuine scope change.
**Audience:** Architects validating that the new docs extend, rather than contradict, the established portfolio design.
**Purpose:** Show that every ownership claim is traceable to an existing source, and surface the single dependency that expands an existing repository's charter.

---

## The Extraction Thesis

The corpus currently places AI behavior inside `unfurl-foundry` and the AI component family (`unfurl_components_ai`: llm, embedding, vector, document.chunk, rag, agent). `unfurl-foundry-substrate` does not move that ownership — it **extracts the thin contract layer** out of it, exactly as `unfurl-substrate` was extracted from the deterministic orchestrator.

The deterministic side already did this split: shapes and ports live in `unfurl-substrate`; durable/distributed execution and concrete executors live in `unfurl-flow`. This proposal applies the identical split to the probabilistic side: agent/tool/model/rag/embedding shapes and ports live in `unfurl-foundry-substrate`; durable/distributed agent execution, concrete provider adapters, vector DB, per-tenant registries, and the server live in `unfurl-foundry`.

No product loses or gains a responsibility it did not already have. `unfurl-foundry` still owns "probabilistic orchestration: agents, models, RAG, embeddings, vector, multi-agent composition"; it now sits *on top of* a named substrate instead of containing the contract types inline.

Implementation note: the Java reactor now includes `foundry-substrate-serialization` (`FoundrySubstrateCodec`) alongside the domain/ports/engine modules, because stable JSON/YAML round trips are part of the contract surface. The ports module also includes `NodeExecutor` adapters for `agent.run`, `tool.call`, `rag.search`, and `provider.call`; these are the direct flow-facing bridge for static capability registration, while `foundry-substrate-offers` remains the DCP/contract bridge.

Testing module exception: `foundry-substrate-testing` intentionally depends on the fixture-facing modules it needs, but not `foundry-substrate-engine`. The engine consumes those fixtures at test scope, so a testing-to-engine dependency would create a reactor cycle. This is a structural exception to any shorthand phrasing that says testing depends on "all modules."

---

## Mapping To HLD-A / HLD-B Subsystems

The Intelligent Components HLDs define the AI subsystems. Each maps cleanly onto a foundry-substrate **port** (the contract) with the concrete subsystem implemented in `unfurl-foundry`:

| HLD-B subsystem | foundry-substrate (the contract) | `unfurl-foundry` (the implementation) |
|---|---|---|
| Provider Registry | `ProviderRegistry` / `ModelProvider` / `EmbeddingProvider` ports | per-tenant registration, Fernet-encrypted creds, TTL cache, concrete adapters |
| RAG Pipeline | `RagRetriever` / `VectorStore` ports, RAG query/result/provenance shapes | ingestion DAG, pgvector client, extractors |
| Tool Registry | `ToolRegistry` / `ToolExecutor` ports, `ToolDefinition` shape | YAML-directory loading, sandboxed code, MCP sub-registry, LangChain bridge |
| PermissionBridge | `PermissionBridge` port | per-tenant policy resolution, persisted requests/approvals |
| Planning (LLM-assisted DAG draft) | — | out of scope; stays in foundry (it calls a model to generate drafts) |

The "lightweight consumption patterns" from HLD-B (Provider Registry + Tool Registry + small model; Provider Registry + RAG) remain possible: a consumer takes the foundry-substrate ports plus a foundry adapter, without the durable engine.

---

## Mapping To The DCP Three Planes (HLD-C / HLD-C2)

The dynamic-composition design is a direct application of DCP's three planes, with each plane assigned to the repository the corpus already names:

- **Plane 1 (Description, no intelligence):** components publish claims. foundry-substrate supplies the **AI offer fragments** that fill the claim's `offers` section; the claim schema is `unfurl-dcp`'s.
- **Plane 2 (Negotiation, intelligence, design-time):** `unfurl-fabric` reasons about accept/reject/refusal and freezes a composition contract. Unchanged from HLD-C ("Fabric owns Plane 2 negotiation").
- **Plane 3 (Invocation, no intelligence):** execution against the frozen contract. foundry-substrate supplies the AI `ContractInvocable` impls; the in-process path reuses the substrate dispatcher.

This honors HLD-C's central principle verbatim — *negotiation is a compile step; invocation is execution* — and the residency wedge: intelligence touches Planes 1 and 2 at design time and never touches Plane 3 in the perimeter.

---

## The One Genuine Scope Expansion: the `unfurl-dcp` Runtime Broker

The README and architecture currently describe `unfurl-dcp` as owning "claim and contract schemas." This design additionally assigns `unfurl-dcp` a **runtime composition broker**: the deterministic component that, at runtime, takes a presented claim, looks up the matching fabric-frozen contract, returns a disposition (accept/partial/refuse with fabric's pre-computed redirection), and on accept registers the guest's offers into the host's `CapabilityRegistry` as `ContractInvocable`-backed executors.

This is the only place the new docs go beyond a literal reading of the existing corpus, so it is flagged explicitly:

- **Why DCP and not the substrate or foundry-substrate:** DCP already owns the claim/contract schemas the broker operates on; keeping the broker there keeps all DCP concerns in one place and lets *any* host (flow, foundry, others) gain dynamic composition without depending on the AI substrate. This was the user's explicit decision.
- **Why it does not break the wedge:** the broker is deterministic — frozen-contract lookup, no model, no reasoning, no phone-home. All intelligence remains in fabric at design time.
- **Dependency it implies:** the broker depends on `substrate-ports` (`CapabilityRegistry`) and `substrate-composition-api` (`ContractInvocable`) — both already designated in the substrate LLD as "safe for DCP-facing consumers" (`unfurl-dcp can implement ContractInvocable by depending on substrate-composition-api`). So the dependency direction is already anticipated by the substrate design.

**Action required outside this doc set:** `unfurl-dcp` needs its own broker spec (an LLD section or build-spec phase) defining: the claim-presentation API, the deterministic match/disposition algorithm, and the `CapabilityRegistry` registration/unregistration contract. The broker interface that `foundry-substrate-offers` targets is sketched in the foundry-substrate LLD ("Composition Flow"); the dcp spec must make it authoritative. This is a hard dependency for foundry-substrate's `offers` module but is out of scope for the present deliverables.

---

## Boundaries Otherwise Preserved

- **`unfurl-flow`** keeps "no AI dependency": a workflow with no agent node runs with zero model involvement. Flow gains AI capability only by registering foundry-substrate executors, and AI reasoning runs behind ports / over frozen contracts.
- **`unfurl-fabric`** keeps design-time negotiation; nothing intelligent moves into the runtime.
- **`unfurl-foundry`** keeps probabilistic orchestration and now names its substrate.
- **`unfurl-substrate`** remains the deterministic peer layer; foundry-substrate depends on it and reuses `ExecutionContext`, `Event`, `CapabilityRegistry`, `ContractInvocable`, `EdgeDefinition`, `ConditionDefinition`, and the resolver. The local Java implementation now wires `substrate-resolver` into the embedded substrate engine, closing the narrow "resolver declared but not used" gap from the substrate review; broader deterministic gaps such as loops/triggers/interaction execution and durable resume remain substrate-owned work.
- **Multi-phase agents** stay as DAGs of phases joined by conditional edges (master doc decision: "multi-phase agents as default"); no new workflow model is introduced.
- **ADR-001 language scope:** the foundry-substrate docs target Java (JDK 21), consistent with `unfurl-substrate` and `unfurl-dcp`. DCP-contract interop, not a language mandate, remains the cross-product boundary.
