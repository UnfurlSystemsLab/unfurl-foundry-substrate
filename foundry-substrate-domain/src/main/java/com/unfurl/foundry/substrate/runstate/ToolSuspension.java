package com.unfurl.foundry.substrate.runstate;

import com.unfurl.foundry.substrate.model.ModelToolCall;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Memento: exact bounded tool transaction before authorization; stores execution data, never grants dispatch. */
public record ToolSuspension(String tenantId, String agentRunId, String phaseId, String toolCallId,
        String approvalId, String providerCallId, String toolName, Map<String, Object> arguments,
        Map<String, Object> requestMetadata, String modelRef, int completedToolIterations, int maxToolIterations,
        List<ModelToolCall> remainingCalls, String responseContent, Instant suspendedAt) {

    /** Canonical constructor: pins identity and loop position and deeply freezes JSON request/batch payloads. */
    public ToolSuspension {
        if (tenantId != null) required(tenantId, "tenantId");
        required(agentRunId, "agentRunId"); required(phaseId, "phaseId"); required(toolCallId, "toolCallId");
        required(approvalId, "approvalId"); required(providerCallId, "providerCallId"); required(toolName, "toolName");
        required(modelRef, "modelRef"); Objects.requireNonNull(suspendedAt, "suspendedAt is required");
        if (maxToolIterations < 1 || completedToolIterations < 0 || completedToolIterations >= maxToolIterations)
            throw new IllegalArgumentException("suspended tool iteration bounds are invalid");
        arguments = ExecutionJsonSnapshot.freeze(arguments);
        requestMetadata = ExecutionJsonSnapshot.freeze(requestMetadata);
        if (!Objects.equals(tenantId, requestMetadata.get("tenantId"))
                || !agentRunId.equals(requestMetadata.get("agentRunId")) || !phaseId.equals(requestMetadata.get("phaseId"))
                || !toolCallId.equals(requestMetadata.get("toolCallId")) || !approvalId.equals(requestMetadata.get("approvalId")))
            throw new IllegalArgumentException("suspended tool request scope changed");
        if (remainingCalls == null || remainingCalls.isEmpty() || remainingCalls.size() > 256)
            throw new IllegalArgumentException("suspended tool batch must contain 1 to 256 calls");
        remainingCalls = remainingCalls.stream().map(call -> {
            required(call.id(), "providerCallId"); required(call.toolName(), "toolName");
            return new ModelToolCall(call.id(), call.toolName(), ExecutionJsonSnapshot.freeze(call.arguments()));
        }).toList();
        if (!providerCallId.equals(remainingCalls.getFirst().id()) || !toolName.equals(remainingCalls.getFirst().toolName()))
            throw new IllegalArgumentException("suspended tool batch does not begin with pending call");
        responseContent = responseContent == null ? "" : responseContent;
    }

    /** Identity guard: disallows empty identifiers rather than inferring provider or tenant state. */
    private static void required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
