package com.unfurl.foundry.substrate.context;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Value-object contract tests for the retrieved-record extension of the context selection request. */
class ContextSelectionRequestTest {
    private static final ContextPolicy POLICY = new ContextPolicy(List.of(), List.of(), List.of(), false, false, 100, Map.of());

    /** Compatibility: the pre-retrieval constructor yields an explicit empty retrieved list. */
    @Test
    void compatibilityConstructorHasNoRetrievedRecords() {
        var request = new ContextSelectionRequest(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                List.of(), List.of(), POLICY, Map.of());

        assertThat(request.retrieved()).isEmpty();
    }

    /** Immutability: records are copied in rank order and later caller mutation cannot change them. */
    @Test
    void freezesRetrievedRecordsInRankOrder() {
        Map<String, Object> first = new HashMap<>(Map.of("chunkId", "a#0"));
        List<Map<String, Object>> records = new ArrayList<>(List.of(first, Map.of("chunkId", "b#0")));
        var request = new ContextSelectionRequest(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                List.of(), List.of(), records, POLICY, Map.of());
        first.put("chunkId", "changed");
        records.clear();

        assertThat(request.retrieved()).extracting(record -> record.get("chunkId")).containsExactly("a#0", "b#0");
        assertThatThrownBy(() -> request.retrieved().getFirst().put("x", 1)).isInstanceOf(UnsupportedOperationException.class);
    }

    /** Validation: a null record is rejected rather than silently dropped. */
    @Test
    void rejectsNullRetrievedRecord() {
        List<Map<String, Object>> records = new ArrayList<>();
        records.add(null);

        assertThatThrownBy(() -> new ContextSelectionRequest(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                List.of(), List.of(), records, POLICY, Map.of())).isInstanceOf(NullPointerException.class);
    }
}
