# Using `unfurl-foundry-substrate`

Practical guide for the AI-side substrate library family. For design background see [HLD-unfurl-foundry-substrate.md](../HLD-unfurl-foundry-substrate.md) and [LLD-unfurl-foundry-substrate-java.md](../LLD-unfurl-foundry-substrate-java.md). For how it lines up with the deterministic substrate see [RECONCILIATION.md](../RECONCILIATION.md).

---

## What this layer is

`unfurl-foundry-substrate` is the AI peer of `unfurl-substrate`. Where the deterministic substrate owns workflow / node / wait-resume shapes, the foundry substrate owns the *probabilistic* shapes — agents, tools, prompts, models, RAG, embeddings, cost accounting — plus the AI capability **offers** (`agent.run`, `tool.call`, `rag.search`, `provider.call`) and `NodeExecutor` adapters that let `unfurl-flow` register AI capabilities through its normal substrate `CapabilityRegistry`.

It ships a minimal embedded agent runner. Durable execution, streaming, concrete model SDKs, vector-DB drivers, credential storage — none of those live here. They live in `unfurl-foundry` or in adapters above.

```
unfurl-substrate           workflow / node / wait shapes, embedded engine
unfurl-foundry-substrate   agent / phase / model / tool / rag shapes,
                           AI offers, agent.run / tool.call / rag.search
                           / provider.call NodeExecutors, embedded runner
unfurl-foundry             durable / distributed / streaming, concrete
                           model SDKs, vector stores, credential stores
unfurl-flow                consumes the offers + adapters as plain substrate
                           capabilities
```

## When to use what

| You are... | You need |
|---|---|
| Defining an agent (phases, edges, tools) | `foundry-substrate-domain` |
| Writing a model / embedding provider adapter | `foundry-substrate-ports` |
| Writing a tool implementation | `foundry-substrate-ports` (`ToolExecutor`) |
| Wiring a RAG retriever | `foundry-substrate-ports` + `foundry-substrate-rag` |
| Running an agent in-process (tests, demos) | `foundry-substrate-engine` |
| Exposing `agent.run` / `tool.call` / `rag.search` to substrate-aware hosts | `foundry-substrate-offers` |
| Loading agent YAML/JSON | `foundry-substrate-serialization` |
| Building test fakes | `foundry-substrate-testing` |

Never depend on `foundry-substrate-engine` from production code unless your product *is* the embedded runner.

## Install

All eleven artifacts release together. Pin once, pull the modules you need:

```xml
<properties>
  <foundry-substrate.version>0.1.0-SNAPSHOT</foundry-substrate.version>
</properties>

<dependencies>
  <dependency>
    <groupId>com.unfurl.foundry.substrate</groupId>
    <artifactId>foundry-substrate-domain</artifactId>
    <version>${foundry-substrate.version}</version>
  </dependency>
  <dependency>
    <groupId>com.unfurl.foundry.substrate</groupId>
    <artifactId>foundry-substrate-ports</artifactId>
    <version>${foundry-substrate.version}</version>
  </dependency>
  <dependency>
    <groupId>com.unfurl.foundry.substrate</groupId>
    <artifactId>foundry-substrate-testing</artifactId>
    <version>${foundry-substrate.version}</version>
    <scope>test</scope>
  </dependency>
</dependencies>
```

JDK 21.

---

## 1. Define an agent

An agent is a static DAG of phases joined by conditional edges — the same scheduling shape as substrate workflows, only the nodes do reasoning instead of integration.

```java
import com.unfurl.foundry.substrate.agent.*;
import com.unfurl.substrate.domain.EdgeDefinition;
import java.util.List;
import java.util.Map;

AgentPhase classify = new AgentPhase(
    "classify",
    "classify-template",                        // promptTemplateRef
    "claude-opus-4-7",                          // modelRef
    List.of(),                                  // allowedToolRefs
    null,                                       // ragQueryRef
    Map.of("text", "$.input.text"),             // input
    Map.of("category", "$.output.category"),    // outputMapping
    List.of(),                                  // dependencies
    0);                                         // maxToolIterations

AgentPhase resolve = new AgentPhase(
    "resolve",
    "resolve-template",
    "claude-opus-4-7",
    List.of("kb.search", "ticket.create"),
    "kb-query",
    Map.of("category", "$.phases.classify.output.category"),
    Map.of(),
    List.of("classify"),
    8);

AgentDefinition agent = new AgentDefinition(
    "triage-bot",
    "1.0.0",
    Map.of("owner", "platform"),
    List.of(classify, resolve),
    List.of(new EdgeDefinition("classify", "resolve", null)),
    Map.of(),                                   // inputSchema
    "claude-opus-4-7",                          // defaultModelRef
    List.of("kb.search", "ticket.create"),     // toolRefs
    BudgetPolicy.none());
```

Validate before running:

```java
new AgentDefinitionValidator().validate(agent);
// throws on duplicate phase ids, missing edge endpoints, unknown tool refs, …
```

---

## 2. Implement a tool

A tool is an in-process executor identified by name. The runner enforces an `allowedToolRefs` allow-list per phase, and bounds tool calls per phase via `maxToolIterations`.

```java
import com.unfurl.foundry.substrate.ports.*;
import com.unfurl.substrate.policy.ExecutionContext;
import java.util.Map;

public final class KbSearchTool implements ToolExecutor {
    @Override
    public ToolCallResult execute(ToolCallRequest request, ExecutionContext context) {
        String query = (String) request.arguments().get("query");
        // hit your KB...
        return ToolCallResult.success(Map.of(
            "hits", List.of(Map.of("id", "kb_001", "title", "Refund policy"))));
    }
}
```

Register in a `ToolRegistry`:

```java
import com.unfurl.foundry.substrate.tools.DefaultToolRegistry;

DefaultToolRegistry tools = new DefaultToolRegistry();
tools.register("kb.search",     new KbSearchTool());
tools.register("ticket.create", new TicketCreateTool());
```

---

## 3. Provide a model

A `ModelProvider` invokes a chat/completion model. The substrate ships no SDK — provide your own thin adapter or use the test fakes.

```java
import com.unfurl.foundry.substrate.model.*;
import com.unfurl.foundry.substrate.ports.*;

public final class AnthropicProvider implements ModelProvider {
    private final AnthropicClient client;
    public AnthropicProvider(AnthropicClient client) { this.client = client; }
    @Override
    public ModelResponse complete(ModelRequest request, ExecutionContext context) {
        return new ModelResponse(
            request.requestId(),
            new Message("assistant", client.send(request).text()),
            List.of(),
            Map.of("inputTokens", 240, "outputTokens", 60));
    }
}
```

Expose providers via a `ProviderRegistry`:

```java
import com.unfurl.foundry.substrate.testing.StaticProviderRegistry;

StaticProviderRegistry providers = new StaticProviderRegistry()
    .register("claude-opus-4-7", new AnthropicProvider(client));
```

For tests, use `EchoModelProvider` or `ScriptedModelProvider`.

---

## 4. Run an agent embedded

`EmbeddedAgentRuntime` is the in-process reference runner — the agent peer of substrate's `EmbeddedExecutionEngine`. Sequential, dependency-ordered phase execution; bounded tool-call loop per phase; injected ports throughout; in-memory run state.

```java
import com.unfurl.foundry.substrate.engine.*;
import com.unfurl.foundry.substrate.runstate.*;
import com.unfurl.foundry.substrate.prompt.PromptTemplate;
import com.unfurl.foundry.substrate.testing.*;

EmbeddedAgentRuntime runtime = new EmbeddedAgentRuntime(
    providers,                                     // ProviderRegistry
    tools);                                        // ToolRegistry

AgentRunState run = runtime.start(
    agent,
    Map.of("text", "I want to cancel my order"),
    ExecutionContext.empty());

System.out.println(run.status());                 // COMPLETED
run.phases().forEach((id, p) ->
    System.out.println(id + " → " + p.output()));
System.out.println("tokens: " + run.cost().tokens());
```

Override the defaults when you need a real RAG retriever, a real cost guardrail, custom prompt templates, or a recording event sink:

```java
EmbeddedAgentRuntime runtime = new EmbeddedAgentRuntime(
    providers,
    tools,
    new MyVectorRagRetriever(vectorStore),         // RagRetriever
    new BudgetPolicyCostGuardrail(),               // CostGuardrail
    new AllowAllPermissionBridge(),                // PermissionBridge
    new MyEventSink(),                             // AgentEventSink
    new PromptAssembler(),
    new DataReferenceResolver(),
    new AgentDefinitionValidator(),
    new InMemoryAgentRunStore(),
    Map.of(
        "classify-template", PromptTemplate.of("Classify this: {{text}}"),
        "resolve-template",  PromptTemplate.of("Resolve {{category}} with tools")));
```

The runner captures tool approval as a real `WAITING` transaction. `resume()` returns the saved
snapshot without dispatch; executable, authorized durable continuation remains Foundry-owned.
A harness cannot replace a suspended child with another model turn.

---

## 5. Plug into `unfurl-flow` as a capability

The whole point of this layer is that a flow workflow can have an `agent.run` node and dispatch through its normal substrate `CapabilityRegistry`. The adapter is one line of wiring:

```java
import com.unfurl.foundry.substrate.ports.adapters.*;
import com.unfurl.foundry.substrate.offers.AiOffers;

flowCapabilityRegistry.register(
    AiOffers.AGENT_RUN,                            // "agent.run"
    new AgentRuntimeNodeExecutor(runtime, agent));

flowCapabilityRegistry.register(
    AiOffers.TOOL_CALL,                            // "tool.call"
    new ToolExecutorNodeExecutor(tools));

flowCapabilityRegistry.register(
    AiOffers.RAG_SEARCH,                           // "rag.search"
    new RagRetrieverNodeExecutor(ragRetriever));

flowCapabilityRegistry.register(
    AiOffers.PROVIDER_CALL,                        // "provider.call"
    new ModelProviderNodeExecutor(providers));
```

A workflow then references AI by `uses: agent.run` and gets a deterministic substrate node experience while reasoning runs inside the agent runtime.

```yaml
- id: triage
  type: ACTION
  uses: agent.run
  input:
    text: $.workflow.input.message
```

---

## 6. Expose offers via DCP

The DCP offers for these capabilities are canonical and live in `AiOffers`:

```java
import com.unfurl.foundry.substrate.offers.AiOffers;

List<Offer> offers = AiOffers.standardAiOffers("1.0.0");
// agent.run, tool.call, rag.search, provider.call
```

Add them to your component's `Claim`. When a host accepts a frozen contract for, say, `agent.run`, the `FoundryContractInvocableFactory` builds a `ContractInvocable` that adapts to the runtime port — the consumer dispatches through DCP, the provider runs an agent.

```java
import com.unfurl.foundry.substrate.offers.FoundryContractInvocableFactory;

ContractInvocableFactory factory = new FoundryContractInvocableFactory(
    runtime, tools, ragRetriever, providers);
```

Hand this to `DefaultCompositionBroker.accept(...)`. The capability registered on the host is now backed by your AI runtime.

---

## 7. Cost accounting & guardrails

Per-run cost capture is first-class. Every `AgentRunState` carries `CostAccounting` with attribution and a token meter. Wire a real `CostGuardrail` to decide whether a request proceeds:

```java
import com.unfurl.foundry.substrate.guardrail.*;

CostGuardrail guard = new BudgetPolicyCostGuardrail();   // honors BudgetPolicy
AgentDefinition agent = new AgentDefinition(
    "triage-bot", "1.0.0", Map.of(), List.of(...),
    List.of(), Map.of(), "claude-opus-4-7", List.of(),
    new BudgetPolicy(
        new BigDecimal("0.50"),         // perRunUsdCap
        100_000,                         // perRunTokenCap
        Map.of()));                      // attribution

GuardrailDecision decision = guard.evaluate(
    new CostGuardrailContext(agent, currentRunCost, requestedSpend),
    ExecutionContext.empty());
if (!decision.allowed()) {
    throw new IllegalStateException("budget exceeded: " + decision.reason());
}
```

`AllowAllCostGuardrail` is the no-op default for tests. Aggregation, dashboards, quota state, and billing are downstream concerns (`unfurl-foundry`).

For runtime authorization of tool calls, implement `PermissionBridge`. The default is `AllowAllPermissionBridge` — replace before exposing anything sensitive.

---

## 8. RAG

`RagRetriever` is the port; `Chunk` / `RagQuery` / `RagResult` are the shapes.

```java
import com.unfurl.foundry.substrate.ports.RagRetriever;
import com.unfurl.foundry.substrate.rag.*;

public final class PgVectorRetriever implements RagRetriever {
    @Override
    public RagResult retrieve(RagQuery query, ExecutionContext context) {
        // embed query, search pgvector, return chunks
        return new RagResult(List.of(new Chunk(
            "doc_42", "Refund policy ...", 0.86, Map.of())));
    }
}
```

A phase references a stored query by `ragQueryRef`; the runner calls the retriever before assembling the prompt, and inlines the chunks via the prompt template.

The embedding / vector packages provide shapes (`EmbeddingProvider`, `VectorStore` ports) but no concrete implementations.

---

## 9. Test fixtures

`foundry-substrate-testing` ships drop-in fakes for the ports:

```java
import com.unfurl.foundry.substrate.testing.*;

ProviderRegistry providers = new StaticProviderRegistry()
    .register("test-model", new ScriptedModelProvider(List.of(
        ModelResponse.text("First call response"),
        ModelResponse.text("Second call response"))));

RecordingToolExecutor weatherTool = new RecordingToolExecutor(
    args -> ToolCallResult.success(Map.of("temp", 72)));

DefaultToolRegistry tools = new DefaultToolRegistry()
    .register("weather.get", weatherTool);

EmbeddedAgentRuntime runtime = new EmbeddedAgentRuntime(providers, tools);
runtime.start(agent, Map.of(), ExecutionContext.empty());

assertThat(weatherTool.invocations()).hasSize(1);
```

Available fixtures:

- `EchoModelProvider` — echoes input as the assistant reply.
- `ScriptedModelProvider` — replays a list of canned responses in order.
- `StaticProviderRegistry` — fluent `register(name, provider)`.
- `RecordingToolExecutor` — invokes a lambda and records every call.

---

## Surface map

| Module | Purpose | Key types |
|---|---|---|
| `foundry-substrate-domain` | Agent / phase / budget shapes + validator | `AgentDefinition`, `AgentPhase`, `BudgetPolicy`, `AgentDefinitionValidator` |
| `foundry-substrate-ports` | All AI ports + flow-side `NodeExecutor` adapters | `AgentRuntime`, `ModelProvider`, `ProviderRegistry`, `ToolExecutor`, `ToolRegistry`, `RagRetriever`, `EmbeddingProvider`, `VectorStore`, `AgentEventSink`, adapters in `ports.adapters` |
| `foundry-substrate-prompt` | Prompt template + assembly | `PromptTemplate`, `PromptAssembler` |
| `foundry-substrate-tools` | Default tool registry | `DefaultToolRegistry` |
| `foundry-substrate-rag` | RAG query / chunk / result | `Chunk`, `RagQuery`, `RagResult` |
| `foundry-substrate-resolver` | `agentRef`/`toolRef`/phase-input resolution | `DataReferenceResolver` |
| `foundry-substrate-serialization` | YAML/JSON codecs for public AI records | `FoundrySubstrateCodec` |
| `foundry-substrate-offers` | DCP offers + `ContractInvocable` factory | `AiOffers`, `AgentInvocation`, `ToolInvocation`, `RagInvocation`, `ProviderInvocation`, `FoundryContractInvocableFactory` |
| `foundry-substrate-engine` | In-process embedded runner | `EmbeddedAgentRuntime`, `InMemoryAgentRunStore`, `AllowAllCostGuardrail`, `AllowAllPermissionBridge`, `NoopAgentEventSink` |
| `foundry-substrate-events` | AI event schema | `AgentEvent`, `AgentEventType` |
| `foundry-substrate-testing` | Test fakes | `EchoModelProvider`, `ScriptedModelProvider`, `StaticProviderRegistry`, `RecordingToolExecutor` |

Cost / guardrail / permission types live in `com.unfurl.foundry.substrate.guardrail` (across `ports` and `engine` modules): `CostGuardrail`, `BudgetPolicyCostGuardrail`, `CostGuardrailContext`, `GuardrailDecision`, `PermissionBridge`, `PermissionDecision`.

---

## What this layer does *not* do

- It does **not** ship a model SDK — Anthropic / OpenAI / Azure / Ollama adapters live above (`unfurl-foundry`).
- It does **not** persist runs durably — `InMemoryAgentRunStore` is in-memory only; `unfurl-foundry` owns durable / distributed.
- It does **not** stream responses — streaming belongs to `unfurl-foundry`.
- It does **not** store credentials — per-tenant secret storage is `unfurl-foundry`.
- It does **not** train or fine-tune models — training pipelines are outside the runtime stack entirely.
- It does **not** broker DCP contracts — `unfurl-dcp` owns the runtime broker. This layer only contributes offers and a `ContractInvocableFactory`.
- It does **not** make negotiation decisions — `unfurl-fabric` owns design-time intelligence; runtime is deterministic against frozen contracts.
- It does **not** select models or write prompts on Unfurl's behalf — that's product / customer territory.

If you find yourself adding an SDK, persistence, streaming, credential storage, negotiation, or prompt content here, stop. You're in the wrong layer.
