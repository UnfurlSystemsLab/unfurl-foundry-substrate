# Implementation Backlog: Graph-Backed Agent Harness Contracts

**Status:** Approved architecture translated into implementation slices. Slices 1-5 are implemented; Slices 6-7 remain planned.
**Scope:** `unfurl-foundry-substrate`, `unfurl-foundry`, and the narrow `unfurl-flow` `agentRef` integration.
**Governing design:** `HLD-unfurl-foundry-substrate.md`, `LLD-unfurl-foundry-substrate-java.md`, and the repository build specs.

## Delivery Rules

- Preserve `AgentDefinition` as the canonical graph and `AgentHarnessRuntime` as the execution interface.
- Make every public-model change additive for one compatibility window. Existing YAML/JSON and Java constructors must continue to work until all three repositories consume the new contract.
- Publish and consume in dependency order: `unfurl-foundry-substrate`, then `unfurl-foundry`, then `unfurl-flow`.
- Put provider/MCP SDK code only in Foundry adapters. Substrate modules remain no-I/O and SDK-free.
- Enforce policy through ports and DCP bindings. Prompts may guide behavior but never replace prerequisite, permission, approval, budget, or validation gates.
- Each slice is independently releasable and must leave the reactor green before the next slice starts.

## Slice 1: Provider-Neutral Turn Outcome

**Status:** Implemented and verified in `unfurl-foundry-substrate` and `unfurl-foundry`.

**Goal:** stop runtime branching on provider strings such as Anthropic `tool_use` or `end_turn`.

### `unfurl-foundry-substrate`

- Add `foundry-substrate-domain/.../model/ModelTurnOutcome.java` with `TOOL_REQUESTED`, `COMPLETED`, `MAX_OUTPUT_REACHED`, `CONTENT_FILTERED`, and `PROVIDER_ERROR`.
- Add `ModelTurnOutcome outcome` to `ModelResponse` while retaining `finishReason` for one compatibility window.
- Add an overloaded legacy constructor that derives `outcome` from tool calls plus a conservative legacy mapping. Unknown non-empty legacy values must not become successful completion silently.
- Update `EmbeddedAgentRuntime` to branch on `outcome`; a non-empty tool-call list must agree with `TOOL_REQUESTED`.
- Update `FoundrySubstrateCodecTest`, model record tests, scripted providers, and engine tests.

### `unfurl-foundry`

- Update `AnthropicModelProvider`, `GeminiModelProvider`, and Spring AI bindings to map native completion reasons explicitly.
- Add exhaustive adapter tests for every native reason and an unknown-reason failure case.

### Acceptance

- Existing serialized `finishReason` payloads still deserialize.
- New payloads serialize both fields during the compatibility window; `outcome` is authoritative.
- The engine loops only for `TOOL_REQUESTED`, completes only for `COMPLETED`, and returns structured failures for other non-success outcomes.
- `mvn test` passes in substrate and Foundry.

## Slice 2: Structured Failures And Empty Success

**Status:** Implemented and verified in `unfurl-foundry-substrate` and `unfurl-foundry`.

**Goal:** give agents actionable, sanitized failures without confusing an empty result with an error.

### `unfurl-foundry-substrate`

- Add `FailureCategory` and `StructuredFailure` under `com.unfurl.foundry.substrate.failure`. The failure carries code, category, retryability, optional retry delay, sanitized message, partial output, details, and provenance.
- Categories are `VALIDATION`, `AUTHORIZATION`, `NOT_FOUND`, `BUSINESS_RULE`, `RATE_LIMIT`, `TRANSIENT`, `PROVIDER`, and `INTERNAL`.
- Extend `ToolCallResult` additively with `StructuredFailure failure`.
- Retain `errorCode`/`errorMessage` compatibility access or constructors for one window; `failure` is canonical.
- Add factories `success`, `emptySuccess`, and `failure(StructuredFailure)`.
- Update `ToolInvocation`, `ToolExecutorNodeExecutor`, tool-result message serialization, codecs, and tests.

### `unfurl-foundry`

- Update `HttpToolExecutor`, deployment tool plugins, terminal validators, and public failure mapping to create sanitized structured failures.
- Do not expose stack traces, credentials, prompts, raw provider bodies, or internal endpoints.

### Acceptance

- `{success:true, output:{}}` remains a successful empty result and is never retried as `NOT_FOUND`.
- `retryable=false` prevents automated retry. `retryable=true` is eligible only under a configured retry limit and deadline.
- Existing four-field `ToolCallResult` construction compiles unchanged.
- Tool, DCP invocation, HTTP, codec, and server tests cover every category and partial-result behavior.

## Slice 3: Terminal Envelope And Harness Migration

**Status:** Implemented and verified in `unfurl-foundry-substrate`, `unfurl-foundry`, and `unfurl-flow`.

**Goal:** replace arbitrary last-phase output with a stable provider-neutral `agent.run` result.

### `unfurl-foundry-substrate`

- Add `AgentTerminalStatus`: `COMPLETED`, `WAITING_FOR_USER`, `WAITING_FOR_APPROVAL`, `ESCALATED`, `GAP`, `FAILED`, and `CANCELLED`.
- Add `AgentTerminalEnvelope`: status, output, questions, handoff, field-level confidence, provenance, structured failure, metering, and metadata.
- Add `TerminalEnvelopeNormalizer` Strategy. It accepts the current `kind`/`questions`/`unmet` convention during migration and emits the canonical envelope.
- Change `AgentHarnessObservation` and `AgentHarnessRunState` additively so the canonical envelope is retained without losing legacy output.
- Update `EmbeddedAgentHarnessRuntime` and serialization tests.

### `unfurl-foundry`

- Update `AgentRunInvocable` to return only a validated terminal envelope on new contract versions.
- Extend durable checkpoints to persist harness status, questions, approval metadata, child observations, and terminal envelope.
- Update `FoundryDcpServer` response schemas and `FoundryOpenApiProjector` descriptors.

### `unfurl-flow`

- Update `ContractInvocableNodeExecutor` to interpret terminal status: `COMPLETED` finishes the node; waiting statuses return the normal pending result; gap, escalation, and failure follow declared node policy.
- Persist the envelope with DCP audit and metering metadata.

### Acceptance

- Existing deployment agents emitting `kind` continue through normalization.
- Missing, malformed, or disallowed terminal output fails with `AGENT_OUTPUT_INVALID`.
- Start, clarification, approval, resume, gap, escalation, failure, cancellation, and checkpoint recovery are covered end to end.

## Slice 4: Resolved `agentRef` Contract

**Status:** Implemented and verified in `unfurl-foundry-substrate` and `unfurl-flow`.

**Goal:** make invocation reproducible and auditable without teaching Flow agent semantics.

### `unfurl-foundry-substrate`

- Add `ResolvedAgentReference` in the resolver module with pinned id/version, canonical definition digest, input and terminal schema refs, harness policy, dependency provenance, budget/permission constraints, and DCP binding identity.
- Add a canonical digest service over the stable codec representation. Exclude mutable deployment secrets and runtime state.
- Add deterministic round-trip and digest property tests.

### `unfurl-flow`

- Update `AgentRefResolver` to produce `ResolvedAgentReference` metadata instead of budget-only metadata.
- Keep `ResolvedReference` generic; do not add agent-specific fields to it.
- Update `ResolvedWorkflowCodec`, CLI output, resolution tests, and property tests.
- Require `uses: agent.run` when `agentRef` is present; a reference alone is not executable.

### Acceptance

- Equivalent definitions produce byte-stable resolved YAML and the same digest; any semantic definition change changes the digest.
- Unresolvable or version-mismatched refs fail at load time.
- Flow passes resolved metadata unchanged and still imports no provider SDK.

## Slice 5: Deterministic Tool Interceptor Chain

**Status:** Implemented in `unfurl-foundry-substrate` and `unfurl-foundry`.

**Goal:** enforce prerequisites, approval, permission, normalization, and redaction around every tool call.

### `unfurl-foundry-substrate`

- Add `ToolCallInterceptor`, `ToolCallDecision`, and `ToolCallInterceptorChain` to ports/tools.
- Use Chain of Responsibility: before interceptors run in configuration order; after interceptors run in reverse order.
- Decisions are `ALLOW`, `DENY`, or `REQUIRE_APPROVAL`; normalized arguments are optional and permissions remain monotone.
- Integrate the chain into `EmbeddedAgentRuntime` before registry execution and after the result.
- Ship an empty no-op chain only; do not ship permissive production policy.

### `unfurl-foundry`

- Add `policy/` implementations for prerequisite tokens, human approval, result normalization/redaction, and audit metadata.
- Persist approval waits in the durable harness store and resume through an explicit signal.
- Assemble chains from runtime profiles; fail boot when a declared binding is absent.

### Acceptance

- A denied or unmet-prerequisite call never reaches `ToolExecutor`.
- Approval suspends before the side effect and resumes at most once.
- After interceptors cannot convert a denial into success or restore redacted data.
- Unit tests prove ordering, monotone permissions, sanitization, and event sequence.

## Slice 6: Schema And Semantic Validation

**Goal:** guarantee output shape and validate meaning with bounded correction.

### `unfurl-foundry-substrate`

- Add optional output schema refs and `CorrectionPolicy` to `AgentPhase` and terminal metadata through backward-compatible constructors.
- Add `SemanticValidator`, `ValidationResult`, and `ValidationIssue` contracts.
- Validate in order: structural schema, output mapping, semantic validator.
- Feed specific validation issues into a correction turn only when policy permits; cap attempts and duration.

### `unfurl-foundry`

- Add validator registry/profile bindings and deterministic validators outside prompts.
- Adapt existing `terminalValidators` deployment metadata to the new port during migration.

### Acceptance

- Structurally invalid output never reaches a semantic validator.
- Valid-but-wrong output receives precise correction feedback.
- Exhaustion returns a structured validation failure or escalation envelope.
- No validation path can loop without finite attempt and time bounds.

## Slice 7: Context Policy, Delegation, And MCP Adapter

**Goal:** add explicit context control and governed coordinator/subagent execution, then expose MCP through neutral ports.

### `unfurl-foundry-substrate`

- Add `ContextPolicy`, `ContextSelectionRequest/Result`, `ContextResource`, and `ContextResourceProvider`.
- Add `AgentDelegationRequest/Result` and the `AgentDelegate` Strategy port.
- Add a sequential embedded delegate for tests/reference use. It projects explicit context and propagates structured child failures.
- Compose child cost and permission with lower-of budget and permission intersection.

### `unfurl-foundry`

- Add `context/` selectors, pinned-fact preservation, summary/checkpoint persistence, provenance preservation, and token allocation.
- Add `composition/` coordinator runtime with bounded parallel child execution and deterministic aggregation.
- Add an `mcp/` Adapter mapping MCP tools to `ToolDefinition`/`ToolExecutor` and resources to `ContextResourceProvider`.
- Confine MCP SDK, transport, credentials, discovery cache, and lifecycle to `mcp/` and deployment profiles.

### Acceptance

- A child sees only its declared objective, context projection, sources, schema, budget, and permissions.
- Context compaction preserves pinned facts, unresolved questions/approvals, provenance, governance, and definition digest.
- Child transient failure, non-retryable failure, empty success, and partial results reach the coordinator distinctly.
- An MCP tool traverses the same interceptor, permission, budget, event, and DCP path as a native tool.
- No MCP type or SDK appears in substrate, core runtime, Flow, or public DCP domain models.

## Cross-Slice Verification Matrix

| Concern | Substrate | Foundry | Flow |
|---|---|---|---|
| Public-record compatibility | Codec and constructor tests | Deployment YAML fixtures | Resolved workflow fixtures |
| Provider neutrality | Outcome enum and engine tests | Native-reason adapter tests | No provider imports |
| Governance | Port and monotonicity tests | Concrete policy/approval tests | Outer envelope propagation |
| Durability | In-memory state semantics | Checkpoint/recovery tests | Node pending/resume tests |
| DCP | Offer/invocable mapping | Server contract tests | Registered capability audit trail |
| Security | No-I/O and ArchUnit | Sanitization and tenant isolation | No AI/provider SDK |
| Provenance | Digest and source records | Checkpoint/event propagation | Resolved-view persistence |

## Release And Migration Order

1. Land Slice 1 in substrate, publish a snapshot, adapt Foundry providers, and remove runtime string branching.
2. Land Slice 2 before interceptors so every later policy path has one failure vocabulary.
3. Land Slice 3 and update Foundry/Flow consumers before making the terminal envelope mandatory.
4. Land Slice 4 and regenerate resolved workflow fixtures.
5. Land Slices 5 and 6; enable them through explicit profiles while legacy deployments migrate.
6. Land Slice 7 last because it depends on terminal envelopes, structured failures, governance, and resolved refs.
7. After one coordinated compatibility release, remove legacy `finishReason`, four-field failure wire output, and raw `kind` interpretation in a separately documented breaking release.

## Definition Of Done

- The same pinned graph runs through embedded and durable harnesses with the same terminal semantics.
- Flow invokes the graph as one `agent.run` node and never schedules an internal reasoning phase.
- Provider completion reasons, MCP types, credentials, and SDK types remain behind adapters.
- Every tool and child-agent call is policy-gated, budgeted, permission-scoped, attributable, and failure-structured.
- Context and delegation are explicit; no hidden transcript or memory inheritance exists.
- Compatibility, unit, property, architecture, DCP, durability, air-gap, and cross-repository integration tests pass.
