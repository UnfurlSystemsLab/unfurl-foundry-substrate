package com.unfurl.foundry.substrate.offers;

import com.unfurl.foundry.substrate.ports.RagRetriever;
import com.unfurl.foundry.substrate.rag.Chunk;
import com.unfurl.foundry.substrate.rag.RagQuery;
import com.unfurl.foundry.substrate.rag.RagResult;
import com.unfurl.substrate.composition.ContractInvocable;
import com.unfurl.substrate.composition.ContractInvocation;
import com.unfurl.substrate.composition.ContractInvocationResult;
import com.unfurl.substrate.policy.ExecutionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Exposes the {@code rag.search} capability over a frozen DCP contract. */
/**
 * class for the Foundry AI substrate surface; documents the RagInvocation contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public final class RagInvocation implements ContractInvocable {
    private final String contractId;
    private final String contractVersion;
    private final RagRetriever retriever;

/**
 * Constructs RagInvocation with the dependencies or value fields required by this component and preserves constructor validation invariants.
 */
    public RagInvocation(String contractId, String contractVersion, RagRetriever retriever) {
        this.contractId = contractId;
        this.contractVersion = contractVersion;
        this.retriever = retriever;
    }

/**
 * Implements the contractId helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    @Override
    public String contractId() {
        return contractId;
    }

/**
 * Implements the contractVersion helper for this component, preserving the surrounding input, output, and edge-case contract.
 */
    @Override
    public String contractVersion() {
        return contractVersion;
    }

/**
 * Performs the invoke operation for this component, translating validated inputs into the domain result expected by callers.
 */
    @Override
    public ContractInvocationResult invoke(ContractInvocation invocation, ExecutionContext context) {
        Map<String, Object> input = invocation.input();
        String query = String.valueOf(input.getOrDefault("query", ""));
        int topK = input.get("topK") instanceof Number n ? n.intValue() : 5;
        String collectionRef = input.get("collectionRef") instanceof String s ? s : null;
        RagResult result = retriever.retrieve(new RagQuery(query, topK, Map.of(), collectionRef, Map.of()), context);
        List<Map<String, Object>> chunks = new ArrayList<>();
        for (Chunk chunk : result.chunks()) {
            chunks.add(Map.of("id", chunk.id(), "text", chunk.text(), "score", chunk.score()));
        }
        return ContractInvocationResult.success(Map.of("chunks", chunks));
    }
}
