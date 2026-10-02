// unfurl-foundry-substrate — C4 architecture model (as built), Structurizr DSL.
// Generated from docs/prompts/Structurizr DSL Generator.md; reconciled on 2026-10-02 with the current implementation.
// concern labels are descriptive categories, not named DCP claim ports. claimNeed records actual catalog needs.
// Every element and relationship cites its evidence. This repository deploys nothing, so there is no
// deployment view; the backlog (docs/IMPLEMENTATION-agent-harness-contract-backlog.md) reports slices
// 1-7 implemented, so no Planned elements exist.
// Render: docker run -it --rm -p 8090:8080 -v <repo>/docs/architecture:/usr/local/structurizr structurizr/lite

workspace "Unfurl Foundry Substrate" "Provider-neutral Java 21 agent library. Convention: because this repository has no deployables, Level 2 'containers' are published Maven modules (tag 'Library Module'); Level 3 components are package groupings and key types. Module dependency edges are direct Maven compile dependencies; sample edges are separately labelled DCP needs. ArchitectureTest checks allowed Java class dependencies (not exact POM equality) and excludes the optional Spring AI adapter. The substrate opens no sockets: every network interaction belongs to a host-supplied implementation and is tagged 'Host-owned'. Neutral Java SPI seams are modelled as 'Extension Point' components with 'Customer-supplied' implementations." {

    !identifiers hierarchical

    model {

        // ------------------------------------------------------------------ People

        // source: docs/use/README.md (embedding guide); EmbeddedAgentRuntime public constructors
        developer = person "Host Application Developer" "Embeds the library, supplies port implementations and agent definitions."

        // ------------------------------------------------------------------ Consumers

        // source: ../unfurl-foundry/pom.xml (foundry-substrate-domain, -engine, -events, -offers, -ports, -serialization, -springai-adapter)
        foundry = softwareSystem "Unfurl Foundry" "Governed agent product. Consumes domain, engine, events, offers, ports, serialization and springai-adapter." "Consumer"
        // source: ../unfurl-flow/pom.xml (foundry-substrate-domain, -ports, -resolver)
        flow = softwareSystem "Unfurl Flow" "Deterministic workflow runtime. Consumes domain, ports and resolver." "Consumer"
        // source: docs/use/README.md
        embedding_host = softwareSystem "Embedding Host Application" "Any third-party JVM application that embeds the substrate directly." "Consumer"
        // source: ../unfurl-fabric/src/main/java/com/unfurl/fabric/catalog/CatalogManifestCodec.java (reads META-INF/unfurl-catalog.yaml)
        fabric = softwareSystem "Unfurl Fabric" "Design-time composition compiler; scans module DCP catalog claims." "External"

        // ------------------------------------------------------------------ Upstream libraries

        // source: */pom.xml (com.unfurl.substrate:substrate-domain, -events, -ports, -resolver, -composition-api)
        unfurl_substrate = softwareSystem "unfurl-substrate" "Shared substrate modules: domain, events, ports/policy (ExecutionContext), resolver, composition API." "External Library"
        // source: foundry-substrate-offers/pom.xml (com.unfurl.dcp:unfurl-dcp); ArchitectureTest.dcpDependenciesStayInOffers
        unfurl_dcp = softwareSystem "unfurl-dcp" "DCP claims, contracts and ContractInvocable broker types." "External Library"
        // source: foundry-substrate-springai-adapter/pom.xml (spring-ai-model, spring-ai-vector-store)
        spring_ai = softwareSystem "Spring AI" "spring-ai-model and spring-ai-vector-store APIs (ChatModel, EmbeddingModel, VectorStore, ToolCallback)." "External Library"
        // source: foundry-substrate-domain/pom.xml, -serialization/pom.xml (jackson-databind, -dataformat-yaml, -datatype-jsr310, jakarta.validation-api)
        jackson = softwareSystem "Jackson & Jakarta Validation" "JSON/YAML data binding, Java Time module and Bean Validation annotations." "External Library"

        // ------------------------------------------------------------------ Host-owned vendors

        // source: docs/HLD-unfurl-foundry-substrate.md (No phone-home); ArchitectureTest.productionCodeDoesNotUseSdkOrInfrastructureClients
        model_vendors = softwareSystem "Model & Vector Vendors" "LLM, embedding and vector-store services. Reached only by host-supplied implementations, never by substrate code." "External,LLM"

        // ------------------------------------------------------------------ Customer-supplied implementations (extensibility)

        // source: unfurl-substrate ExecutionContext (tenantId, userId, roles, permissions, correlationId, requestId, traceContext); ../dcp/docs/elements/integration-ports.md (authentication)
        host_identity = softwareSystem "Host Identity & Access" "Authenticates callers and supplies ExecutionContext. The substrate defines no authentication port (Host-owned)." "Customer-supplied"
        // source: guardrail/PermissionBridge.java, ports/ToolCallInterceptor.java; ../dcp/docs/elements/integration-ports.md (authorization)
        host_policy = softwareSystem "Host Policy Engine" "Permission and tool-call policy decisions (allow, deny, require approval)." "Customer-supplied"
        // source: guardrail/CostGuardrail.java, runstate/CostAccounting.java
        host_cost = softwareSystem "Host Cost & Usage Ledger" "Persistent quotas, reservations, pricing and billing beyond the in-run budget check." "Customer-supplied"
        // source: ports/AgentEventSink.java, events/AgentEventType.java; ../dcp/docs/elements/integration-ports.md (telemetry, monitoring)
        host_telemetry = softwareSystem "Host Telemetry Collector" "Receives agent, phase, model, token, tool, RAG, guardrail and validation events for monitoring and tracing." "Customer-supplied"
        // source: engine/ExternalCallBoundary.java, AgentRunStore.java, AgentHarnessStateStore.java
        host_audit = softwareSystem "Host Audit & Durable Store" "Journals external calls and persists run and harness state durably." "Customer-supplied"
        // source: ports/ModelProvider.java, EmbeddingProvider.java, VectorStore.java, RagRetriever.java; claims model-provider@v1, embedding-provider@v1, vector-store@v1, rag.corpus@v1?owner=host, spring-ai.*@v1?owner=host
        host_ai = softwareSystem "Host Model, Embedding & Vector Providers" "Concrete ModelProvider / EmbeddingProvider / VectorStore implementations or Spring AI beans." "Customer-supplied"
        // source: ports/ToolRegistry.java, ToolExecutor.java; foundry-substrate-tools claim need tool.implementation@v1?owner=host
        host_tools = softwareSystem "Host Tool Implementations" "ToolExecutor implementations registered by the host." "Customer-supplied"
        // source: ports/OutputSchemaValidator.java, SemanticValidator.java, SemanticValidatorRegistry.java
        host_validation = softwareSystem "Host Validators" "Output-schema and semantic (domain-meaning) validators." "Customer-supplied"
        // source: ports/ContextSelector.java, ContextResourceProvider.java, ModelRequestProjector.java
        host_context = softwareSystem "Host Context Providers" "Context selection, read-only context resources and model-request projection." "Customer-supplied"
        // source: ports/AgentToolContinuation.java, SuspendedToolExecutor.java, AgentHarnessChildContinuation.java
        host_continuation = softwareSystem "Host Durable Continuation" "Claims, authorizes and dispatches a suspended tool attempt at most once; supplies the SuspendedToolExecutor." "Customer-supplied"
        // source: docs/HLD-unfurl-foundry-substrate.md (credentials stay with host providers); ../dcp/docs/elements/integration-ports.md (secrets, configuration)
        host_secrets = softwareSystem "Host Secrets & Configuration" "Provider credentials and configuration. The substrate defines no secrets or configuration port (Host-owned)." "Customer-supplied"

        // ------------------------------------------------------------------ The library

        substrate = softwareSystem "Unfurl Foundry Substrate" "Provider-neutral agent domain, ports, embedded runtime and DCP offers, published as com.unfurl.foundry.substrate:* JARs." "Library" {

            group "Domain & Events" {
                // source: foundry-substrate-domain/pom.xml; packages agent, context, delegation, embedding, failure, model, prompt, rag, runstate, skill, terminal, tool
                domain = container "foundry-substrate-domain" "Agent, harness, skill, prompt, model, tool, RAG, context, delegation, failure, terminal and run-state records (incl. BudgetPolicy, CostAccounting, ToolSuspension)." "Java 21 library JAR (com.unfurl.foundry.substrate:foundry-substrate-domain)" "Library Module"
                // source: foundry-substrate-events/pom.xml; events/AgentEvent.java, AgentEventType.java
                events = container "foundry-substrate-events" "AI event schema: AgentEvent and AgentEventType." "Java 21 library JAR (com.unfurl.foundry.substrate:foundry-substrate-events)" "Library Module"
            }

            group "Ports" {
                // source: foundry-substrate-ports/pom.xml; packages ports, ports.adapters, guardrail
                ports = container "foundry-substrate-ports" "Neutral runtime, provider, tool, policy, validation, context and continuation ports plus guardrail defaults." "Java 21 library JAR (com.unfurl.foundry.substrate:foundry-substrate-ports)" "Library Module" {
                    // source: ports/AgentRuntime.java, AgentHarnessRuntime.java, AgentDelegate.java, AgentDefinitionResolver.java, SkillRegistry.java
                    runtime_ports = component "Runtime Ports" "AgentRuntime, AgentHarnessRuntime, AgentDelegate, AgentDefinitionResolver, SkillRegistry." "Java interfaces"
                    // source: ports/adapters/AgentRuntimeNodeExecutor.java, ModelProviderNodeExecutor.java, RagRetrieverNodeExecutor.java, ToolExecutorNodeExecutor.java
                    node_adapters = component "Substrate Node Executor Adapters" "Expose AgentRuntime, ModelProvider, RagRetriever and ToolExecutor as substrate NodeExecutors." "Java"
                    // source: ports/ModelProvider.java, ProviderRegistry.java, ProviderKind.java, EmbeddingProvider.java, VectorStore.java, RagRetriever.java
                    ai = component "AI Provider Ports" "ModelProvider, ProviderRegistry, EmbeddingProvider, VectorStore, RagRetriever. Implemented by springai-adapter or the host; RAG retrieval is off unless a RagRetriever is bound." "Java interfaces" "Extension Point" {
                        properties {
                            "concern" "ai"
                            "claimNeed" "model-provider@v1; embedding-provider@v1; vector-store@v1; rag.corpus@v1?owner=host; spring-ai.chat-client@v1?owner=host; spring-ai.embedding-client@v1?owner=host; spring-ai.vector-store@v1?owner=host"
                            "status" "Open port + adapter"
                            "defaultImplementation" "none (springai-adapter and test fixtures available)"
                        }
                    }
                    // source: ports/ToolRegistry.java, ToolExecutor.java; tools/DefaultToolRegistry.java
                    tools_ports = component "Tool Ports" "ToolRegistry and ToolExecutor. DefaultToolRegistry ships; executors are host-owned." "Java interfaces" "Extension Point" {
                        properties {
                            "concern" "tools"
                            "claimNeed" "tool.implementation@v1?owner=host"
                            "status" "Open port"
                            "defaultImplementation" "DefaultToolRegistry (registry only)"
                        }
                    }
                    // source: guardrail/PermissionBridge.java, PermissionDecision.java; ports/ToolCallInterceptor.java, ToolCallInterceptorChain.java, ToolCallDecision.java, ToolCallScope.java; engine/AllowAllPermissionBridge.java
                    authorization = component "Authorization Ports" "PermissionBridge and the ToolCallInterceptor chain returning ALLOW / DENY / REQUIRE_APPROVAL with an engine-minted ToolCallScope. Defaults allow everything." "Java interfaces" "Extension Point" {
                        properties {
                            "concern" "authorization"
                            "status" "No-op default (allow-all)"
                            "defaultImplementation" "AllowAllPermissionBridge; ToolCallInterceptorChain.empty()"
                        }
                    }
                    // source: guardrail/CostGuardrail.java, CostGuardrailContext.java, GuardrailDecision.java, BudgetPolicyCostGuardrail.java; domain BudgetPolicy.lowerOf, runstate/CostAccounting.java
                    cost = component "Cost & Budget Guardrails" "CostGuardrail with CostGuardrailContext (agentBudgetPolicy, outerBudgetRemainingUsd) and lower-of budget composition; TOKENS_CONSUMED metering." "Java" "Extension Point" {
                        properties {
                            "concern" "cost (no standard DCP port name)"
                            "status" "Implemented default"
                            "defaultImplementation" "BudgetPolicyCostGuardrail (no I/O, current-run snapshot only)"
                        }
                    }
                    // source: ports/AgentEventSink.java, CorrectionProgressObserver.java; events/AgentEventType.java; EmbeddedAgentRuntime.defaultEventSink()
                    telemetry = component "Event & Telemetry Ports" "AgentEventSink for metering-grade agent, phase, model, token, tool, RAG, guardrail and validation events; CorrectionProgressObserver." "Java interfaces" "Extension Point" {
                        properties {
                            "concern" "telemetry, monitoring"
                            "status" "No-op default"
                            "defaultImplementation" "no-op event sink; CorrectionProgressObserver.noop()"
                        }
                    }
                    // source: ports/OutputSchemaValidator.java, SemanticValidator.java, SemanticValidatorRegistry.java, ValidationResult.java; EmbeddedAgentRuntime short constructor passes null validators
                    validation = component "Validation Ports" "OutputSchemaValidator and SemanticValidator(Registry) driving the bounded correction loop. Not bound by default." "Java interfaces" "Extension Point" {
                        properties {
                            "concern" "validation (no standard DCP port name)"
                            "status" "Open port"
                            "defaultImplementation" "none"
                        }
                    }
                    // source: ports/ContextSelector.java, ContextResourceProvider.java, ModelRequestProjector.java (identity())
                    context = component "Context Ports" "ContextSelector, ContextResourceProvider and ModelRequestProjector applied before every model call." "Java interfaces" "Extension Point" {
                        properties {
                            "concern" "context (no standard DCP port name)"
                            "status" "Implemented default (identity)"
                            "defaultImplementation" "ModelRequestProjector.identity()"
                        }
                    }
                    // source: ports/AgentToolContinuation.java, SuspendedToolExecutor.java, AgentHarnessChildContinuation.java, CorrectionProgress.java
                    continuation = component "Continuation Ports" "AgentToolContinuation, SuspendedToolExecutor and AgentHarnessChildContinuation for host-governed resumption of approval waits." "Java interfaces" "Extension Point" {
                        properties {
                            "concern" "durable continuation (no standard DCP port name)"
                            "status" "Open port"
                            "defaultImplementation" "none, by design (no default executor)"
                        }
                    }
                }
            }

            group "Building Blocks" {
                // source: foundry-substrate-prompt/pom.xml; prompt/PromptAssembler.java
                prompt = container "foundry-substrate-prompt" "PromptAssembler: renders pinned prompt templates." "Java 21 library JAR (com.unfurl.foundry.substrate:foundry-substrate-prompt)" "Library Module"
                // source: foundry-substrate-tools/pom.xml; tools/DefaultToolRegistry.java; claim offer tool.invoke
                tools = container "foundry-substrate-tools" "DefaultToolRegistry; offers tool.invoke." "Java 21 library JAR (com.unfurl.foundry.substrate:foundry-substrate-tools)" "Library Module"
                // source: foundry-substrate-rag/pom.xml; rag/VectorStoreRagRetriever.java; claim offer rag.search
                rag = container "foundry-substrate-rag" "VectorStoreRagRetriever; offers rag.search." "Java 21 library JAR (com.unfurl.foundry.substrate:foundry-substrate-rag)" "Library Module"
                // source: foundry-substrate-resolver/pom.xml; resolver/AgentReferenceResolver.java, DataReferenceResolver.java, ResolvedAgentReferenceFactory.java
                resolver = container "foundry-substrate-resolver" "agentRef/toolRef resolution and phase-input data references." "Java 21 library JAR (com.unfurl.foundry.substrate:foundry-substrate-resolver)" "Library Module"
                // source: foundry-substrate-serialization/pom.xml; serialization/FoundrySubstrateCodec.java, CanonicalAgentDefinitionDigest.java
                serialization = container "foundry-substrate-serialization" "JSON/YAML codec (Java Time, BigDecimal-preserving) and canonical agent-definition digests." "Java 21 library JAR (com.unfurl.foundry.substrate:foundry-substrate-serialization)" "Library Module"
            }

            group "DCP Offers" {
                // source: foundry-substrate-offers/pom.xml; ArchitectureTest.dcpDependenciesStayInOffers
                offers = container "foundry-substrate-offers" "DCP offers, claim projectors and ContractInvocable factories; the only module importing unfurl-dcp." "Java 21 library JAR (com.unfurl.foundry.substrate:foundry-substrate-offers)" "Library Module" {
                    // source: offers/AiOffers.java
                    ai_offers = component "AI Offers" "Canonical DCP offers and matching fault declarations for the AI capabilities." "Java"
                    // source: offers/AgentInvocation.java, ToolInvocation.java, RagInvocation.java, ProviderInvocation.java
                    invocables = component "AI Contract Invocables" "AgentInvocation, ToolInvocation, RagInvocation and ProviderInvocation exposing agent, tool, RAG and provider capabilities over frozen DCP contracts." "ContractInvocable"
                    // source: offers/FoundryContractInvocableFactory.java
                    invocable_factory = component "Contract Invocable Factory" "DCP broker bridge that materializes the AI invocables." "ContractInvocableFactory"
                    // source: offers/FoundryClaimProjector.java, FlowClaimProjector.java
                    projectors = component "Claim Projectors" "Project foundry-substrate agent graphs and Flow workflow graphs into DCP claim views." "Java"
                    // source: offers/RecursiveProjectionRequestCli.java (main)
                    projection_cli = component "Projection Request CLI" "Developer tool: converts Flow workflow YAML and Foundry agent YAML into the JSON request accepted by Fabric Studio's dynamic-dcp/project route. Performs no network I/O." "Java CLI"
                }
            }

            group "Embedded Runtime" {
                // source: foundry-substrate-engine/pom.xml; claim urn:unfurl:foundry:engine offers agent.execute
                engine = container "foundry-substrate-engine" "Embedded, in-process agent and harness runtimes with sequential delegation; offers agent.execute." "Java 21 library JAR (com.unfurl.foundry.substrate:foundry-substrate-engine)" "Library Module" {
                    // source: engine/EmbeddedAgentRuntime.java (implements AgentRuntime, AgentToolContinuation)
                    agent_runtime = component "Embedded Agent Runtime" "Bounded agent loop: validate, resolve input, retrieve, budget check, assemble prompt, project request, call model, run tools, validate output, save state, emit events. Captures approval waits and continues them via AgentToolContinuation." "Java"
                    // source: engine/EmbeddedAgentHarnessRuntime.java (implements AgentHarnessRuntime, AgentHarnessChildContinuation)
                    harness_runtime = component "Embedded Harness Runtime" "Bounded multi-turn harness with clarification, approval, escalation, cancellation and same-child continuation." "Java"
                    // source: engine/SequentialAgentDelegate.java
                    delegate = component "Sequential Agent Delegate" "Invokes pinned child agents with narrowed permissions and lower-of budgets." "AgentDelegate"
                    // source: engine/ExternalCallBoundary.java (direct()), AgentRunStore.java, AgentHarnessStateStore.java
                    durability = component "Call Boundary & State Stores" "ExternalCallBoundary wraps external dispatch without owning persistence. AgentRunStore exposes save/load only; AgentHarnessStateStore additionally exposes compare-and-set transition for harness claims. Durability, authorization and cross-process coordination belong to the host." "Java interfaces" "Extension Point" {
                        properties {
                            "concern" "audit / durable tracking (no standard DCP port name)"
                            "status" "No-op / in-memory default"
                            "defaultImplementation" "ExternalCallBoundary.direct(); process-local run snapshots; InMemoryAgentHarnessStateStore"
                            "agentStoreOperations" "save/load only; no CAS"
                            "harnessStoreOperations" "save/load/transition; default CAS is process-local"
                        }
                    }
                    // source: EmbeddedAgentRuntime constructors, defaultStore(), defaultEventSink(); InMemoryAgentHarnessStateStore
                    defaults = component "In-process Defaults" "Short constructor binds BudgetPolicyCostGuardrail, AllowAllPermissionBridge, empty tool interceptors, a no-op AgentEventSink and a process-local run snapshot store. Harness uses InMemoryAgentHarnessStateStore. AllowAllCostGuardrail is an optional Strategy, not the runtime default." "Java (Strategy / Null Object)"
                }
            }

            group "Adapters" {
                // source: foundry-substrate-springai-adapter/pom.xml; claim needs spring-ai.*@v1?owner=host
                springai = container "foundry-substrate-springai-adapter" "Implements substrate provider ports over host-supplied Spring AI beans." "Java 21 library JAR (com.unfurl.foundry.substrate:foundry-substrate-springai-adapter)" "Library Module" {
                    // source: springai/SpringAiModelProvider.java (ChatModel, Prompt, ToolCallingChatOptions, ToolCallback)
                    model_provider = component "Spring AI Model Provider" "ModelProvider over org.springframework.ai ChatModel with tool-calling options." "Java"
                    // source: springai/SpringAiEmbeddingProvider.java (EmbeddingModel)
                    embedding_provider = component "Spring AI Embedding Provider" "EmbeddingProvider over org.springframework.ai EmbeddingModel." "Java"
                    // source: springai/SpringAiVectorStoreAdapter.java (VectorStore, SearchRequest, Document)
                    vector_store = component "Spring AI Vector Store Adapter" "VectorStore over org.springframework.ai VectorStore." "Java"
                }
            }

            group "Test Fixtures" {
                // source: foundry-substrate-testing/pom.xml; testing/EchoModelProvider.java, ScriptedModelProvider.java, RecordingToolExecutor.java, StaticProviderRegistry.java
                testing = container "foundry-substrate-testing" "Downstream fixtures: echo and scripted model providers, recording tool executor, static provider registry." "Java 21 library JAR (com.unfurl.foundry.substrate:foundry-substrate-testing)" "Test Fixture"
            }

            group "Samples" {
                // source: sample-chatbot-agent/src/main/resources/META-INF/unfurl-catalog.yaml (needs agent.execute@v1)
                sample_chatbot = container "sample-chatbot-agent" "Catalog-only DCP claim for a chatbot agent." "DCP claim YAML" "Sample Claim"
                // source: sample-research-agent/src/main/resources/META-INF/unfurl-catalog.yaml (needs agent.execute@v1, rag.search@v1)
                sample_research = container "sample-research-agent" "Catalog-only DCP claim for a retrieval-grounded research agent." "DCP claim YAML" "Sample Claim"
                // source: sample-tool-using-agent/src/main/resources/META-INF/unfurl-catalog.yaml (needs agent.execute@v1, tool.invoke@v1)
                sample_tool = container "sample-tool-using-agent" "Catalog-only DCP claim for a tool-using agent." "DCP claim YAML" "Sample Claim"
            }
        }

        // ================================================================== Module edges (Maven compile dependencies)
        // source for every edge in this block: the origin module's pom.xml.
        // ArchitectureTest constrains Java class dependencies; allowed transitive edges are not direct POM edges.

        substrate.domain -> unfurl_substrate "Extends substrate-domain records" "Maven compile dependency"
        substrate.domain -> jackson "Annotates records for binding and validation" "Maven compile dependency"
        substrate.events -> unfurl_substrate "Extends substrate-events" "Maven compile dependency"
        substrate.events -> jackson "Bean Validation annotations" "Maven compile dependency"
        substrate.ports -> substrate.domain "Uses agent, model, tool and run-state records" "Maven compile dependency"
        substrate.ports -> substrate.events "Emits AgentEvent" "Maven compile dependency"
        substrate.ports -> unfurl_substrate "Uses substrate-ports and policy (ExecutionContext)" "Maven compile dependency"
        substrate.prompt -> substrate.domain "Renders PromptTemplate records" "Maven compile dependency"
        substrate.tools -> substrate.ports "Implements ToolRegistry" "Maven compile dependency"
        substrate.rag -> substrate.ports "Implements RagRetriever over VectorStore" "Maven compile dependency"
        substrate.resolver -> substrate.domain "Resolves agent references" "Maven compile dependency"
        substrate.resolver -> substrate.serialization "Decodes referenced definitions" "Maven compile dependency"
        substrate.resolver -> unfurl_substrate "Uses substrate-resolver" "Maven compile dependency"
        substrate.serialization -> substrate.domain "Serializes domain records" "Maven compile dependency"
        substrate.serialization -> jackson "JSON/YAML codec" "Maven compile dependency"
        substrate.offers -> substrate.domain "Projects agent definitions" "Maven compile dependency"
        substrate.offers -> substrate.ports "Wraps runtime and provider ports as invocables" "Maven compile dependency"
        substrate.offers -> unfurl_substrate "Uses substrate-composition-api and substrate-domain" "Maven compile dependency"
        substrate.offers -> unfurl_dcp "Builds claims, offers and ContractInvocables" "Maven compile dependency"
        substrate.offers -> jackson "Reads workflow and agent YAML" "Maven compile dependency"
        substrate.engine -> substrate.prompt "Assembles prompts" "Maven compile dependency"
        substrate.engine -> substrate.tools "Uses the tool registry" "Maven compile dependency"
        substrate.engine -> substrate.rag "Uses RAG retrieval" "Maven compile dependency"
        substrate.engine -> substrate.resolver "Resolves phase inputs and references" "Maven compile dependency"
        substrate.engine -> substrate.offers "Uses offer and invocation types" "Maven compile dependency"
        substrate.engine -> substrate.domain "Executes agent definitions" "Maven compile dependency"
        substrate.engine -> substrate.ports "Uses runtime, provider, policy and continuation ports" "Maven compile dependency"
        substrate.engine -> jackson "Serializes tool result messages" "Maven compile dependency"
        substrate.testing -> substrate.domain "Builds domain fixtures" "Maven compile dependency"
        substrate.testing -> substrate.tools "Provides tool fixtures" "Maven compile dependency"
        substrate.testing -> substrate.rag "Provides retrieval fixtures" "Maven compile dependency"
        substrate.testing -> substrate.ports "Implements provider and tool ports" "Maven compile dependency"
        substrate.springai -> substrate.domain "Maps model and tool records" "Maven compile dependency"
        substrate.springai -> substrate.ports "Implements neutral provider ports" "Maven compile dependency"
        substrate.springai -> jackson "Maps JSON tool arguments" "Maven compile dependency"
        substrate.springai -> spring_ai "Wraps ChatModel, EmbeddingModel and VectorStore" "Maven compile dependency"

        // ================================================================== Sample claims (design time)
        // source: sample-*/META-INF/unfurl-catalog.yaml needs; offers in engine (agent.execute), rag (rag.search), tools (tool.invoke) claims
        substrate.sample_chatbot -> substrate.engine "Needs agent.execute@v1" "DCP need (design time)"
        substrate.sample_research -> substrate.engine "Needs agent.execute@v1" "DCP need (design time)"
        substrate.sample_research -> substrate.rag "Needs rag.search@v1" "DCP need (design time)"
        substrate.sample_tool -> substrate.engine "Needs agent.execute@v1" "DCP need (design time)"
        substrate.sample_tool -> substrate.tools "Needs tool.invoke@v1" "DCP need (design time)"
        // source: ../unfurl-fabric/.../catalog/CatalogManifestCodec.java; every module JAR carries META-INF/unfurl-catalog.yaml
        fabric -> substrate.engine "Scans module DCP catalog claims" "DCP claim YAML (META-INF/unfurl-catalog.yaml, design time)"
        fabric -> substrate.sample_chatbot "Scans sample agent claim" "DCP claim YAML (design time)"
        fabric -> substrate.sample_research "Scans sample agent claim" "DCP claim YAML (design time)"
        fabric -> substrate.sample_tool "Scans sample agent claim" "DCP claim YAML (design time)"

        // ================================================================== Consumers
        // source: ../unfurl-foundry/pom.xml
        foundry -> substrate.engine "Runs agents and harnesses on the embedded engine" "Maven compile dependency"
        foundry -> substrate.ports "Implements and binds substrate ports" "Maven compile dependency"
        foundry -> substrate.offers "Exposes AI capabilities as DCP invocables" "Maven compile dependency"
        foundry -> substrate.serialization "Encodes checkpoints and definitions" "Maven compile dependency"
        foundry -> substrate.springai "Binds Spring AI provider families" "Maven compile dependency"
        // source: ../unfurl-flow/pom.xml
        flow -> substrate.ports "Uses agent and tool port types in workflows" "Maven compile dependency"
        flow -> substrate.resolver "Resolves agent references at load time" "Maven compile dependency"
        flow -> substrate.domain "Reads agent definitions" "Maven compile dependency"
        // source: docs/use/README.md
        embedding_host -> substrate.engine "Embeds EmbeddedAgentRuntime / EmbeddedAgentHarnessRuntime" "Maven compile dependency"
        developer -> substrate.engine.agent_runtime "Constructs and configures the embedded runtime" "Java API"
        developer -> substrate.offers.projection_cli "Projects workflow and agent YAML for Fabric review" "Command line"

        // ================================================================== Component-level (in-process)
        // source: EmbeddedAgentRuntime.java (start path and fields)
        substrate.engine.agent_runtime -> substrate.ports.ai "Calls ModelProvider / RagRetriever" "Java API (in-process)"
        substrate.engine.agent_runtime -> substrate.ports.tools_ports "Looks up and executes tools" "Java API (in-process)"
        substrate.engine.agent_runtime -> substrate.ports.authorization "Evaluates interceptors and PermissionBridge before each tool call" "Java API (in-process)"
        substrate.engine.agent_runtime -> substrate.ports.cost "Checks CostGuardrail before model and tool work" "Java API (in-process)"
        substrate.engine.agent_runtime -> substrate.ports.telemetry "Emits AgentEvents" "Java API (in-process)"
        substrate.engine.agent_runtime -> substrate.ports.validation "Validates output and drives correction" "Java API (in-process)"
        substrate.engine.agent_runtime -> substrate.ports.context "Projects every model request" "Java API (in-process)"
        substrate.engine.agent_runtime -> substrate.ports.continuation "Implements AgentToolContinuation" "Java API (in-process)"
        substrate.engine.agent_runtime -> substrate.engine.durability "Dispatches through ExternalCallBoundary; saves run state" "Java API (in-process)"
        substrate.engine.agent_runtime -> substrate.engine.defaults "Falls back to no-op / in-memory / allow-all defaults" "Java API (in-process)"
        // source: EmbeddedAgentHarnessRuntime.java
        substrate.engine.harness_runtime -> substrate.engine.agent_runtime "Runs bounded inner agent turns" "AgentRuntime (in-process)"
        substrate.engine.harness_runtime -> substrate.engine.durability "Claims waits through AgentHarnessStateStore.transition" "Java API (in-process)"
        substrate.engine.harness_runtime -> substrate.ports.continuation "Implements AgentHarnessChildContinuation" "Java API (in-process)"
        // source: SequentialAgentDelegate.java
        substrate.engine.delegate -> substrate.engine.agent_runtime "Starts pinned child agents" "AgentRuntime (in-process)"
        substrate.engine.delegate -> substrate.ports.runtime_ports "Implements AgentDelegate; resolves definitions" "Java API (in-process)"
        // source: ports/adapters/*NodeExecutor.java
        substrate.ports.node_adapters -> substrate.ports.runtime_ports "Adapts AgentRuntime as a NodeExecutor" "Java API (in-process)"
        substrate.ports.node_adapters -> substrate.ports.ai "Adapts ModelProvider / RagRetriever as NodeExecutors" "Java API (in-process)"
        substrate.ports.node_adapters -> substrate.ports.tools_ports "Adapts ToolExecutor as a NodeExecutor" "Java API (in-process)"
        substrate.ports.node_adapters -> unfurl_substrate "Implements substrate NodeExecutor" "Java API (in-process)"
        // source: offers/*.java
        substrate.offers.invocable_factory -> substrate.offers.invocables "Materializes invocables per frozen contract" "Factory"
        substrate.offers.invocables -> substrate.ports.runtime_ports "Invokes AgentRuntime" "Java API (in-process)"
        substrate.offers.invocables -> substrate.ports.ai "Invokes ModelProvider / RagRetriever" "Java API (in-process)"
        substrate.offers.invocables -> substrate.ports.tools_ports "Invokes ToolExecutor" "Java API (in-process)"
        substrate.offers.invocables -> unfurl_dcp "Implements ContractInvocable" "Java API (in-process)"
        substrate.offers.ai_offers -> unfurl_dcp "Declares offers and faults" "Java API (in-process)"
        substrate.offers.projectors -> unfurl_dcp "Builds claim projections" "Java API (in-process)"
        substrate.offers.projection_cli -> substrate.offers.projectors "Projects workflow and agent graphs" "Java API (in-process)"
        // source: springai/*.java
        substrate.springai.model_provider -> substrate.ports.ai "Implements ModelProvider" "Java API (in-process)"
        substrate.springai.embedding_provider -> substrate.ports.ai "Implements EmbeddingProvider" "Java API (in-process)"
        substrate.springai.vector_store -> substrate.ports.ai "Implements VectorStore" "Java API (in-process)"

        // ================================================================== Host-owned network edges
        // source: springai/* wrap host-supplied beans; ArchitectureTest forbids java.net.http / SDK clients in substrate modules
        substrate.springai.model_provider -> host_ai "Delegates to host-supplied Spring AI ChatModel bean" "Java API (in-process)" "Host-owned"
        substrate.springai.embedding_provider -> host_ai "Delegates to host-supplied Spring AI EmbeddingModel bean" "Java API (in-process)" "Host-owned"
        substrate.springai.vector_store -> host_ai "Delegates to host-supplied Spring AI VectorStore bean" "Java API (in-process)" "Host-owned"
        host_ai -> model_vendors "Calls model, embedding and vector services" "HTTPS (host-owned)" "Host-owned"
        foundry -> model_vendors "Calls vendors through its Spring AI provider families" "HTTPS (host-owned)" "Host-owned"
        embedding_host -> model_vendors "Calls vendors through its own provider implementations" "HTTPS (host-owned)" "Host-owned"

        // ================================================================== Extension edges (customer-supplied implementations)
        // source: §4.2 seams listed on each Extension Point component
        host_identity -> substrate.engine.agent_runtime "Supplies ExecutionContext (tenant, user, roles, permissions, correlation, trace) on every call" "Java API (host-supplied, in-process)" "Extension"
        host_policy -> substrate.ports.authorization "Implements PermissionBridge / ToolCallInterceptor" "Java SPI (host-supplied, in-process)" "Extension"
        host_cost -> substrate.ports.cost "Implements CostGuardrail" "Java SPI (host-supplied, in-process)" "Extension"
        host_telemetry -> substrate.ports.telemetry "Implements AgentEventSink / CorrectionProgressObserver" "Java SPI (host-supplied, in-process)" "Extension"
        host_audit -> substrate.engine.durability "Implements ExternalCallBoundary / AgentRunStore / AgentHarnessStateStore" "Java SPI (host-supplied, in-process)" "Extension"
        host_ai -> substrate.ports.ai "Implements ModelProvider / EmbeddingProvider / VectorStore / RagRetriever" "Java SPI (host-supplied, in-process)" "Extension"
        host_tools -> substrate.ports.tools_ports "Implements ToolExecutor" "Java SPI (host-supplied, in-process)" "Extension"
        host_validation -> substrate.ports.validation "Implements OutputSchemaValidator / SemanticValidator" "Java SPI (host-supplied, in-process)" "Extension"
        host_context -> substrate.ports.context "Implements ContextSelector / ContextResourceProvider / ModelRequestProjector" "Java SPI (host-supplied, in-process)" "Extension"
        host_continuation -> substrate.ports.continuation "Implements SuspendedToolExecutor; drives AgentToolContinuation" "Java SPI (host-supplied, in-process)" "Extension"
        host_secrets -> host_ai "Configures provider credentials and settings" "Host configuration (no substrate port)" "Host-owned"
    }

    views {

        systemContext substrate "SystemContext" "Level 1: the substrate library, its consumers, upstream libraries and host-owned vendors." {
            title "Unfurl Foundry Substrate — System Context"
            include developer foundry flow embedding_host fabric unfurl_substrate unfurl_dcp spring_ai jackson model_vendors
            include substrate
            autoLayout tb 300 200
        }

        container substrate "Modules" "Level 2 (module view): direct Maven compile dependencies grouped by layer; sample DCP needs are separately labelled. ArchitectureTest checks Java layering, not exact POM equality." {
            title "Unfurl Foundry Substrate — Modules"
            include substrate.domain substrate.events substrate.ports substrate.prompt substrate.tools substrate.rag substrate.resolver substrate.serialization
            include substrate.offers substrate.engine substrate.springai substrate.testing substrate.sample_chatbot substrate.sample_research substrate.sample_tool
            include unfurl_substrate unfurl_dcp spring_ai jackson
            autoLayout tb 250 150
        }

        component substrate.ports "PortsComponents" "Level 3: port families in foundry-substrate-ports." {
            title "foundry-substrate-ports — Port Families"
            include substrate.ports.runtime_ports substrate.ports.node_adapters substrate.ports.ai substrate.ports.tools_ports substrate.ports.authorization
            include substrate.ports.cost substrate.ports.telemetry substrate.ports.validation substrate.ports.context substrate.ports.continuation
            include unfurl_substrate
            autoLayout tb 250 150
        }

        component substrate.engine "EngineComponents" "Level 3: embedded runtimes in foundry-substrate-engine and the ports they call." {
            title "foundry-substrate-engine — Embedded Runtime"
            include substrate.engine.agent_runtime substrate.engine.harness_runtime substrate.engine.delegate substrate.engine.durability substrate.engine.defaults
            include substrate.ports substrate.prompt substrate.tools substrate.rag substrate.resolver substrate.offers
            autoLayout tb 250 150
        }

        component substrate.offers "OffersComponents" "Level 3: DCP offers and contract invocables." {
            title "foundry-substrate-offers — DCP Offers"
            include substrate.offers.ai_offers substrate.offers.invocables substrate.offers.invocable_factory substrate.offers.projectors substrate.offers.projection_cli
            include substrate.ports unfurl_dcp developer
            autoLayout tb 250 150
        }

        component substrate.springai "SpringAiComponents" "Level 3: Spring AI adapters over host-supplied beans." {
            title "foundry-substrate-springai-adapter — Adapters"
            include substrate.springai.model_provider substrate.springai.embedding_provider substrate.springai.vector_store
            include substrate.ports host_ai model_vendors
            autoLayout tb 250 150
        }

        component substrate.ports "Extensibility" "Neutral Java SPI extension points with host-supplied implementations; concern metadata is not a DCP claim-port declaration." {
            title "Unfurl Foundry Substrate — Extensibility (open integration ports)"
            include substrate.ports.ai substrate.ports.tools_ports substrate.ports.authorization substrate.ports.cost substrate.ports.telemetry
            include substrate.ports.validation substrate.ports.context substrate.ports.continuation
            include substrate.engine
            include host_identity host_policy host_cost host_telemetry host_audit host_ai host_tools host_validation host_context host_continuation host_secrets
            autoLayout lr 250 120
        }

        // Sequence projection: one successful tool-using phase; repeat bounded model/tool turns as needed.
        // Approval, guardrail and validation failures branch out; events are emitted at their actual boundaries.
        dynamic substrate.engine "EmbeddedAgentRun" "Representative successful tool-using phase, with interleaved state and events. RAG/tools are conditional; bounded model/tool/correction cycles may repeat. REQUIRE_APPROVAL saves WAITING and emits TOOL_APPROVAL_REQUIRED instead of executing the tool." {
            title "Embedded agent run — EmbeddedAgentRuntime.start"
            developer -> substrate.engine.agent_runtime "start: validate definition and create run"
            substrate.engine.agent_runtime -> substrate.engine.durability "Save initial RUNNING snapshot"
            substrate.engine.agent_runtime -> substrate.ports.telemetry "Emit AGENT_STARTED"
            substrate.engine.agent_runtime -> substrate.engine.durability "Resolve phase input; save phase RUNNING"
            substrate.engine.agent_runtime -> substrate.ports.telemetry "Emit PHASE_STARTED"
            substrate.engine.agent_runtime -> substrate.ports.ai "Retrieve grounding when the phase declares a RAG query"
            substrate.engine.agent_runtime -> substrate.ports.telemetry "Emit RAG_RETRIEVED when retrieval runs"
            substrate.engine.agent_runtime -> substrate.ports.cost "Check lower-of budget before model work; rejection emits GUARDRAIL_TRIPPED"
            substrate.engine.agent_runtime -> substrate.ports.context "Assemble prompt; ModelRequestProjector.project"
            substrate.engine.agent_runtime -> substrate.engine.durability "ExternalCallBoundary.invoke(PROVIDER) wraps the model call"
            substrate.engine.agent_runtime -> substrate.ports.ai "ModelProvider returns content or tool calls"
            substrate.engine.agent_runtime -> substrate.ports.telemetry "Emit MODEL_INVOKED and TOKENS_CONSUMED; update accounting"
            substrate.engine.agent_runtime -> substrate.ports.authorization "Before-policy normalization, allow-list and PermissionBridge checks; approval branches to WAITING"
            substrate.engine.agent_runtime -> substrate.ports.telemetry "Emit TOOL_CALLED for an admitted call"
            substrate.engine.agent_runtime -> substrate.engine.durability "ExternalCallBoundary.invoke(TOOL) wraps ToolExecutor"
            substrate.engine.agent_runtime -> substrate.ports.authorization "Run after-call interceptors on the tool result"
            substrate.engine.agent_runtime -> substrate.ports.telemetry "Emit TOOL_COMPLETED on success; TOOL_FAILED on failure"
            substrate.engine.agent_runtime -> substrate.ports.validation "Validate mapped output; bounded correction repeats model/tool boundaries if needed"
            substrate.engine.agent_runtime -> substrate.engine.durability "Save completed phase snapshot"
            substrate.engine.agent_runtime -> substrate.ports.telemetry "Emit PHASE_COMPLETED"
            substrate.engine.agent_runtime -> substrate.engine.durability "Save terminal run after all reachable phases complete"
            substrate.engine.agent_runtime -> substrate.ports.telemetry "Emit AGENT_COMPLETED"
            autoLayout lr 250 150
        }

        styles {
            element "Element" {
                color #ffffff
                stroke #0b3d6b
            }
            element "Person" {
                shape Person
                background #08427b
            }
            element "Software System" {
                background #1168bd
            }
            element "Container" {
                background #438dd5
            }
            element "Component" {
                background #85bbf0
                color #000000
            }
            element "Library Module" {
                shape Component
            }
            element "Sample Claim" {
                shape Folder
                background #6b8fa8
            }
            element "Test Fixture" {
                background #7f9fb5
            }
            element "External Library" {
                background #8a8a8a
                stroke #5c5c5c
            }
            element "External" {
                background #8a8a8a
                stroke #5c5c5c
            }
            element "Consumer" {
                background #2a6f97
            }
            element "LLM" {
                shape Robot
                background #8a8a8a
            }
            element "Extension Point" {
                shape Hexagon
                background #f2c14e
                color #000000
                stroke #a77a00
            }
            element "Customer-supplied" {
                background #ffffff
                color #1f1f1f
                stroke #d1495b
                strokeWidth 4
                border dashed
            }
            relationship "Relationship" {
                dashed false
                color #4a4a4a
            }
            relationship "Host-owned" {
                dashed true
                color #9a9a9a
            }
            relationship "Extension" {
                dashed true
                color #d1495b
            }
        }
    }
}
