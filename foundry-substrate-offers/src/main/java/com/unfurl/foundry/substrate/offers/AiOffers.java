package com.unfurl.foundry.substrate.offers;

import com.unfurl.dcp.claim.ConsumerAccess;
import com.unfurl.dcp.claim.InterfaceKind;
import com.unfurl.dcp.claim.Offer;
import com.unfurl.dcp.claim.OfferInterface;
import com.unfurl.dcp.claim.Stability;
import com.unfurl.dcp.fault.EvidenceSignal;
import com.unfurl.dcp.fault.FaultAffects;
import com.unfurl.dcp.fault.FaultCategory;
import com.unfurl.dcp.fault.FaultDeclaration;
import com.unfurl.dcp.fault.FaultEvidence;
import com.unfurl.dcp.fault.FaultPolicy;
import com.unfurl.dcp.fault.FaultPropagation;
import com.unfurl.dcp.fault.FaultRemediation;
import com.unfurl.dcp.fault.FaultSeverity;
import com.unfurl.dcp.fault.ParentImpact;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Factory: owns the canonical DCP offers and matching fault declarations for
 * the AI capabilities exposed by foundry-substrate.
 */
public final class AiOffers {
    public static final String AGENT_RUN = "agent.run";
    public static final String TOOL_CALL = "tool.call";
    public static final String RAG_SEARCH = "rag.search";
    public static final String PROVIDER_CALL = "provider.call";
    public static final String SKILL_INVOKE = "skill.invoke";
    public static final String MODE_SIMPLE = "simple";
    public static final String MODE_HARNESS = "harness";
    public static final String DETAIL_OPERATION = "operation";
    public static final String DETAIL_INPUT_SHAPE = "inputShape";
    public static final String DETAIL_OUTPUT_SHAPE = "outputShape";
    public static final String DETAIL_EXECUTION_MODES = "execution_modes";
    public static final String DETAIL_DEFAULT_EXECUTION_MODE = "default_execution_mode";
    public static final String DETAIL_MODE_POLICIES = "mode_policies";

/**
 * Constructs AiOffers with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    private AiOffers() {
    }

/**
 * Factory method: creates the standardAiOffers result while keeping caller-facing defaults and validation in one place.
 */
    public static List<Offer> standardAiOffers(String version) {
        return List.of(
                agentRunOffer(version),
                unmetered(TOOL_CALL, "Call an allowed tool", "execute", version, "ToolCallRequest", "ToolCallResult"),
                metered(RAG_SEARCH, "Retrieve grounded context", "retrieve", version, "RagQuery", "RagResult", "tokens"),
                metered(PROVIDER_CALL, "Call a configured model provider", "complete", version, "ModelRequest", "ModelResponse", "tokens"),
                metered(SKILL_INVOKE, "Resolve and invoke a governed skill", "invoke", version,
                        "SkillInvocationRequest", "SkillInvocationResult", "tokens")
        );
    }

/**
 * Factory method: creates the canonical {@code agent.run} offer with both simple and harness
 * execution modes advertised through DCP offer details.
 */
    public static Offer agentRunOffer(String version) {
        return agentRunOffer(version, List.of(MODE_SIMPLE, MODE_HARNESS), MODE_SIMPLE, defaultAgentModePolicies());
    }

/**
 * Factory method: creates an {@code agent.run} offer for a concrete agent or deployment profile.
 * The execution-mode detail lets DCP resolution select harness-capable agents deterministically.
 */
    public static Offer agentRunOffer(
            String version,
            List<String> executionModes,
            String defaultExecutionMode,
            Map<String, Object> modePolicies) {
        List<String> modes = normalizeModes(executionModes);
        String defaultMode = defaultExecutionMode == null || defaultExecutionMode.isBlank()
                ? modes.getFirst()
                : defaultExecutionMode;
        if (!modes.contains(defaultMode)) {
            throw new IllegalArgumentException("default execution mode must be one of executionModes");
        }
        Map<String, Object> details = new LinkedHashMap<>(baseDetails("start", "AgentInput", "AgentOutput"));
        details.put(DETAIL_EXECUTION_MODES, modes);
        details.put(DETAIL_DEFAULT_EXECUTION_MODE, defaultMode);
        details.put(DETAIL_MODE_POLICIES, modePolicies == null ? Map.of() : Map.copyOf(modePolicies));
        return new Offer(
                AGENT_RUN,
                "Run an agent",
                ConsumerAccess.ANY,
                new OfferInterface(InterfaceKind.IN_PROCESS, details),
                Stability.STABLE,
                version,
                true,
                "metered=true; unit=tokens");
    }

/**
 * Factory method: builds the DCP fault policy for a concrete set of AI offers so generated claims keep offer and fault surfaces aligned.
 */
    public static FaultPolicy faultPolicyFor(List<Offer> offers) {
        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        for (Offer offer : offers == null ? List.<Offer>of() : offers) {
            if (offer != null && offer.capability() != null && !offer.capability().isBlank()) {
                capabilities.add(offer.capability());
            }
        }
        List<FaultDeclaration> faults = new ArrayList<>();
        if (capabilities.contains(AGENT_RUN)) {
            faults.add(fault(
                    "agent.run.failed",
                    FaultCategory.RUNTIME,
                    FaultSeverity.BLOCKING,
                    "Agent runtime failed before producing a contract result.",
                    AGENT_RUN,
                    List.of(EvidenceSignal.INVOCATION_ERROR, EvidenceSignal.METRIC_THRESHOLD),
                    ParentImpact.BLOCKED,
                    "parent composition depends on agent.run output",
                    List.of("retry_agent_run", "inspect_agent_state", "rebind_provider")));
        }
        if (capabilities.contains(TOOL_CALL)) {
            faults.add(fault(
                    "tool.call.failed",
                    FaultCategory.CAPABILITY,
                    FaultSeverity.DEGRADED,
                    "Tool invocation failed or was denied by the bound permission policy.",
                    TOOL_CALL,
                    List.of(EvidenceSignal.INVOCATION_ERROR, EvidenceSignal.POLICY_DENIAL),
                    ParentImpact.DEGRADED,
                    "parent composition depends on tool.call result",
                    List.of("retry_tool_call", "repair_tool_binding", "request_operator_approval")));
        }
        if (capabilities.contains(RAG_SEARCH)) {
            faults.add(fault(
                    "rag.search.degraded",
                    FaultCategory.DEPENDENCY,
                    FaultSeverity.DEGRADED,
                    "Retrieval could not return the grounded context required by the claim.",
                    RAG_SEARCH,
                    List.of(EvidenceSignal.INVOCATION_ERROR, EvidenceSignal.METRIC_THRESHOLD),
                    ParentImpact.DEGRADED,
                    "parent composition depends on retrieved grounding",
                    List.of("retry_retrieval", "switch_collection", "repair_vector_store")));
        }
        if (capabilities.contains(PROVIDER_CALL)) {
            faults.add(fault(
                    "provider.call.failed",
                    FaultCategory.DEPENDENCY,
                    FaultSeverity.BLOCKING,
                    "Configured model provider failed or became unavailable.",
                    PROVIDER_CALL,
                    List.of(EvidenceSignal.HEALTH, EvidenceSignal.INVOCATION_ERROR, EvidenceSignal.METRIC_THRESHOLD),
                    ParentImpact.BLOCKED,
                    "parent composition depends on provider.call completion",
                    List.of("retry_provider_call", "switch_provider", "repair_provider_binding")));
        }
        if (capabilities.contains(SKILL_INVOKE)) {
            faults.add(fault(
                    "skill.invoke.failed",
                    FaultCategory.CAPABILITY,
                    FaultSeverity.BLOCKING,
                    "Governed skill invocation failed before satisfying its declared capability.",
                    SKILL_INVOKE,
                    List.of(EvidenceSignal.INVOCATION_ERROR, EvidenceSignal.POLICY_DENIAL),
                    ParentImpact.BLOCKED,
                    "parent composition depends on skill.invoke output",
                    List.of("retry_skill", "repair_skill_binding", "fallback_to_agent_run")));
        }
        return new FaultPolicy(List.copyOf(faults));
    }

/**
 * Builder helper: creates one declared AI fault against a single DCP offer capability.
 */
    private static FaultDeclaration fault(
            String code,
            FaultCategory category,
            FaultSeverity severity,
            String description,
            String capability,
            List<EvidenceSignal> evidence,
            ParentImpact parentImpact,
            String propagatesWhen,
            List<String> remediationActions) {
        return new FaultDeclaration(
                code,
                category,
                severity,
                description,
                new FaultAffects(List.of(), List.of(capability), List.of()),
                new FaultEvidence(evidence),
                new FaultPropagation(parentImpact, propagatesWhen, ""),
                new FaultRemediation(remediationActions));
    }

/**
 * Implements the metered helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private static Offer metered(String capability, String description, String operation, String version, String input, String output, String unit) {
        return offer(capability, description, operation, version, input, output, true, "metered=true; unit=" + unit);
    }

/**
 * Implements the unmetered helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    private static Offer unmetered(String capability, String description, String operation, String version, String input, String output) {
        return offer(capability, description, operation, version, input, output, false, null);
    }

/**
 * Factory method: creates the offer result while keeping caller-facing defaults and validation in one place.
 */
    private static Offer offer(String capability, String description, String operation, String version, String input, String output, boolean metered, String costImplications) {
        return new Offer(
                capability,
                description,
                ConsumerAccess.ANY,
                new OfferInterface(InterfaceKind.IN_PROCESS, baseDetails(operation, input, output)),
                Stability.STABLE,
                version,
                metered,
                costImplications);
    }

/**
 * Builder helper: creates stable DCP offer-interface details while preserving legacy Java
 * shape key names already consumed by existing claims.
 */
    private static Map<String, Object> baseDetails(String operation, String input, String output) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put(DETAIL_OPERATION, operation);
        details.put(DETAIL_INPUT_SHAPE, input);
        details.put(DETAIL_OUTPUT_SHAPE, output);
        return Map.copyOf(details);
    }

/**
 * Normalizer: keeps execution-mode lists non-empty, ordered, and duplicate-free for
 * deterministic DCP claim output.
 */
    private static List<String> normalizeModes(List<String> executionModes) {
        LinkedHashSet<String> modes = new LinkedHashSet<>();
        for (String mode : executionModes == null ? List.<String>of() : executionModes) {
            if (mode != null && !mode.isBlank()) {
                modes.add(mode);
            }
        }
        if (modes.isEmpty()) {
            modes.add(MODE_SIMPLE);
        }
        return List.copyOf(modes);
    }

/**
 * Builder helper: documents the default harness policy exposed by product-level Foundry claims.
 */
    private static Map<String, Object> defaultAgentModePolicies() {
        return Map.of(
                MODE_SIMPLE, Map.of("max_turns_default", 1, "resume", "none"),
                MODE_HARNESS, Map.of("max_turns_default", 4, "max_turns_max", 16, "resume", "in_memory"));
    }
}
