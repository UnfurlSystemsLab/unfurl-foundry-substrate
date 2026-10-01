package com.unfurl.foundry.substrate.ports;

import java.time.Instant;
import java.util.*;

/** Value Object: immutable correction scope/policy and numbered progress, without feedback or business output. */
public record CorrectionProgress(String tenantId, String agentRunId, String phaseId, int attempt,
        int maxAttempts, long maxDurationMillis, String exhaustionAction, Instant startedAt,
        Status status, List<String> issueCodes) {
    /** State vocabulary: a repair request consumes an attempt even when its dispatch cannot commit. */
    public enum Status { STARTED, REPAIR_REQUESTED, COMPLETED, EXHAUSTED, FAILED }

    /** Constructor: validates finite policy, explicit identity, and attempts within the pinned envelope. */
    public CorrectionProgress {
        if (agentRunId == null || agentRunId.isBlank() || phaseId == null || phaseId.isBlank()
                || (tenantId != null && tenantId.isBlank()) || startedAt == null || status == null
                || maxAttempts < 0 || maxDurationMillis < 0 || (maxAttempts > 0 && maxDurationMillis == 0)
                || attempt < 0 || attempt > maxAttempts
                || !("FAIL".equals(exhaustionAction) || "ESCALATE".equals(exhaustionAction))) {
            throw new IllegalArgumentException("invalid correction progress scope or bounds");
        }
        if ((status == Status.STARTED && attempt != 0) || (status == Status.REPAIR_REQUESTED && attempt == 0)) {
            throw new IllegalArgumentException("invalid correction attempt for status");
        }
        issueCodes = List.copyOf(issueCodes);
    }

    /** Projector: exposes stable JSON primitives so record/map serialization cannot relabel progress. */
    public Map<String, Object> metadata() {
        Map<String, Object> values = new LinkedHashMap<>();
        if (tenantId != null) values.put("tenantId", tenantId);
        values.put("agentRunId", agentRunId); values.put("phaseId", phaseId);
        values.put("attempt", attempt); values.put("maxAttempts", maxAttempts);
        values.put("maxDurationMillis", maxDurationMillis); values.put("exhaustionAction", exhaustionAction);
        values.put("startedAt", startedAt.toString());
        values.put("deadline", startedAt.plusMillis(maxDurationMillis).toString());
        values.put("status", status.name()); values.put("issueCodes", issueCodes);
        return Map.copyOf(values);
    }
}
