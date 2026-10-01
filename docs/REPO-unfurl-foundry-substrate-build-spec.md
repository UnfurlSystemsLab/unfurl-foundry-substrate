# REPO: unfurl-foundry-substrate Build Spec

`unfurl-foundry-substrate` is a coordinated Maven module set for Foundry substrate contracts and helpers.

## Build

```bash
mvn test
```

From the workspace root:

```bash
mvn -pl unfurl-foundry-substrate -am test
```

## Test Focus

- Agent substrate record round trips.
- Agent harness definition/state round trips and validation.
- Embedded agent harness turn loop behavior: continue, clarification wait, gap, completion, policy exhaustion.
- Tool, prompt, event, and model boundary contracts.
- Compatibility with `unfurl-foundry` runtime consumers.

## Target Architecture

The canonical representation remains a versioned `AgentDefinition` phase graph. An Anthropic/Codex-style experience is provided by `AgentHarnessRuntime`, which wraps the graph with bounded model/tool/observation turns, clarification, escalation, cancellation, and resume. These are complementary layers, not competing formats.

Flow invokes the complete construct through `uses: agent.run` plus a pinned `agentRef`. Flow owns its surrounding deterministic workflow; Foundry owns the internal agent graph, harness turns, tools, and child-agent calls. Work is delegated back to Flow through DCP `workflow.execute` only when it is a separately declared deterministic workflow requiring Flow-owned durability or coordination. The neutral external-call boundary identifies provider, tool, child-agent, and workflow dispatches; Foundry binds its journal and recovery policy.

The embedded harness uses an injected neutral `AgentHarnessStateStore` Strategy. Its default adapter
is process-local; Foundry may bridge the seam to its versioned checkpoint repository. This is state
access inversion only: durable persistence formats, recovery validation, and migration remain above
the substrate.

## Planned Contract Slices

Slice 8A.2b.4b extends domain runstate with a deeply immutable bounded `ToolSuspension` and
WAITING statuses. The embedded engine captures a real pending transaction through its existing
state-store port, without persistence or authorization code. Harness/delegate adapters retain wait
semantics and do not treat a suspended child as permission to start another turn. Atomic claims,
durable receipt validation, approvals and external-result reconciliation remain product concerns.

4c.3c.2 adds a separate neutral continuation SPI and explicit host-owned pending-result Strategy.
Refactor rather than duplicate the existing loop and scheduler outcome handling. Continue from
saved normalized first request, residual batch, transcript, cost and original timestamps; do not
rerun initial model/RAG/prompt assembly or grant checks in the neutral layer. Shared current budget,
allowlist/permission, after-policy, later-call before-policy, mapping, validation/correction and graph
scheduling still apply. Test restart in a new engine, normalization/scope retention, repeated waits,
loop bounds, failure/uncertainty, guardrails, tenant/state/definition drift and no duplicate model
usage. Install the complete substrate reactor before building Foundry; product dispatch wiring and
durable lifecycle/evidence recovery remain 4c.3c.3.

The implementation-ready file/type/test backlog and migration order are maintained in
[`IMPLEMENTATION-agent-harness-contract-backlog.md`](IMPLEMENTATION-agent-harness-contract-backlog.md).

1. **Neutral turn and terminal contracts:** add `ModelTurnOutcome`, `AgentTerminalEnvelope`, and compatibility decoding for current `finishReason`/`kind` fields.
2. **Structured failures:** extend tool/delegation results with category, retryability, retry delay, sanitized message, partial output, and provenance; preserve successful-empty semantics.
3. **Deterministic tool middleware:** add the `ToolCallInterceptor` Chain of Responsibility around every tool invocation, with monotone permission and approval behavior.
4. **Schema and semantic validation:** declare phase/terminal schemas, add `SemanticValidator`, and support bounded correction feedback before escalation/failure.
5. **Context policy:** add explicit history selection, pinned facts, summaries, tool-result retention, provenance retention, and token allocation without hidden memory.
6. **Governed delegation:** add `AgentDelegate` and explicit child context projection; child budgets use lower-of composition and permissions use intersection.
7. **Resolved `agentRef` view:** preserve `id@version`, definition digest, schema refs, harness policy, dependency provenance, governance, and DCP binding identity.

Each slice must update public-record codec tests, architecture tests, event/error behavior, and Foundry consumer compatibility. No slice may introduce an SDK, transport, persistent store, MCP client, approval queue, or concurrent scheduler into this repository.

## GitHub Packages

Budget context projection must compose inherited and declared policies via `BudgetPolicy.lowerOf`,
not overwrite the caller's spend/token ceilings. Reject present malformed inherited policies before
dispatch. Foundry owns persisted caller authority and continuation authorization through product ports.

This repository participates in the `UnfurlSystemsLab` private Maven package chain.

- Publish: GitHub Actions verifies this repository, then dispatches `UnfurlSystemsLab/unfurl` `publish-lab-maven.yml` with `publish_scope=changed`; the root aggregator publishes this repository's Maven artifacts to `https://maven.pkg.github.com/unfurlsystemslab/unfurl` using Maven server id `github`.
- Consume: this repository resolves internal `com.unfurl...` artifacts through `https://maven.pkg.github.com/unfurlsystemslab/*`.
- Credentials: local and CI Maven settings must provide server id `github`; use `CI_REPO_TOKEN` or a PAT with `repo`, `workflow`, `read:packages`, and `write:packages` for central Lab package dispatch/publish and cross-repository private dependency reads.
- Component CI must use `CI_REPO_TOKEN` for internal package reads and root workflow dispatch; it must fail before Maven verify when that token is unavailable rather than falling back to the repository-scoped `GITHUB_TOKEN`.
- Bootstrap order: publish `unfurl-substrate` and `dcp` before publishing `unfurl-foundry-substrate`.

