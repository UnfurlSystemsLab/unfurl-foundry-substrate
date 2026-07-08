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
                metered(AGENT_RUN, "Run an agent", "start", version, "AgentInput", "AgentOutput", "tokens"),
                unmetered(TOOL_CALL, "Call an allowed tool", "execute", version, "ToolCallRequest", "ToolCallResult"),
                metered(RAG_SEARCH, "Retrieve grounded context", "retrieve", version, "RagQuery", "RagResult", "tokens"),
                metered(PROVIDER_CALL, "Call a configured model provider", "complete", version, "ModelRequest", "ModelResponse", "tokens"),
                metered(SKILL_INVOKE, "Resolve and invoke a governed skill", "invoke", version,
                        "SkillInvocationRequest", "SkillInvocationResult", "tokens")
        );
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
                new OfferInterface(InterfaceKind.IN_PROCESS, Map.of("operation", operation, "inputShape", input, "outputShape", output)),
                Stability.STABLE,
                version,
                metered,
                costImplications);
    }
}
