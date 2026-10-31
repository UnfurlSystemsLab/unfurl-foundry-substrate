# Design Review: Embedded Engine Dependency Isolation

**Status:** Proposed change; implementation pending verification
**Scope:** `foundry-substrate-engine` dependency closure, embedded consumption, execution boundary
**Related:** `HLD-unfurl-foundry-substrate.md`, `LLD-unfurl-foundry-substrate-java.md`, `REPO-unfurl-foundry-substrate-build-spec.md`

## Architectural decision to preserve

The Substrate is a coordinated family of separately consumable JARs, not a feature-flagged monolith. Its embedded runner should provide production-quality **local execution semantics** without owning enterprise recovery policy, durable stores, distributed coordination, or deployment infrastructure. Foundry may reuse this runner and add host-owned operational capabilities. Do not introduce a duplicate Foundry runner solely to enforce a product/substrate split.

## Verified dependency findings

1. `foundry-substrate-engine/pom.xml` directly depends on `foundry-substrate-rag` and `foundry-substrate-offers`.
2. `foundry-substrate-offers/pom.xml` directly depends on `unfurl-dcp`, composition API, and Jackson YAML/JSR310. Thus consumers of the engine receive those dependencies transitively.
3. `EmbeddedAgentRuntime.java` imports `com.unfurl.foundry.substrate.rag.Chunk`, `RagQuery`, and `RagResult`, and has an injected `RagRetriever`. Therefore removing the RAG JAR from the POM without moving/refactoring these types would break compilation.
4. The current HLD/LLD explicitly exclude parallel agent execution and work queues from Substrate. Any worker proposal requires a documented boundary decision before implementation.

These are **dependency and design findings**, not evidence of measured startup/memory overhead. The direct dependency on offers must be checked against *all* engine source imports before removal.

## Required Codex investigation and implementation

### A. Make DCP offers optional for embedded execution

- Audit every production source under `foundry-substrate-engine` for imports and runtime references to `foundry-substrate-offers` and DCP types.
- If no references exist, remove the `foundry-substrate-offers` dependency from the engine POM.
- If references exist, move DCP offer projection/registration into `foundry-substrate-offers` or a narrowly scoped adapter; keep the engine API independent of DCP offer implementations.
- Keep `foundry-substrate-ports` and `domain` independent of concrete hosts and provider SDKs.

### B. Separate optional RAG execution integration

- Identify which RAG records are domain-level shared shapes versus module-level implementations. Avoid creating duplicate `RagQuery`/`RagResult` classes.
- Keep the optional `RagRetriever` port usable without requiring a concrete RAG implementation.
- Evaluate moving the pure RAG record shapes needed by the engine into `foundry-substrate-domain` or a narrow contract module, and relocating retrieval-specific glue to `foundry-substrate-rag`.
- Only remove the engine-to-RAG dependency once all compile-time references are addressed and tests pass. Preserve behavior when no retriever is supplied.

### C. Execution and worker boundary — design only for now

- Preserve sequential in-process execution as the default.
- Investigate whether an optional host-injected executor can be introduced without changing phase transition semantics.
- Do **not** add an internal thread pool, queue, distributed worker, or retry coordinator to the core engine before an HLD decision.
- Host owns concurrency limits, retry/recovery policy, durable checkpoints, and worker lifecycle. Substrate owns correct execution transitions and state validation.

## Acceptance criteria

1. A minimal embedded consumer can depend on the engine without DCP offers and concrete provider SDKs in its resolved runtime dependency tree.
2. A consumer not using RAG is not forced to carry retrieval implementation dependencies; document any unavoidable shared contract JARs and why.
3. Build and test the full substrate reactor and Foundry consumers; preserve agent, harness, tool, RAG, cancellation, and resume behavior.
4. Add a Maven dependency-closure regression check (and architecture test) to prevent accidental host/SDK/DCP-offers dependencies entering the engine.
5. Update HLD and LLD ownership/module diagrams and the build spec only after implementation details are confirmed.
6. Report before/after dependency trees and test results. Do not claim size or performance improvement without measurement.

## Non-goals

No immediate production runner rewrite, no new worker framework, no change to DCP contract semantics, and no persistence or retry policy added to Substrate.
