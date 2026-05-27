package com.unfurl.foundry.substrate.rag;

import com.unfurl.foundry.substrate.ports.RagRetriever;
import com.unfurl.foundry.substrate.ports.VectorStore;
import com.unfurl.substrate.policy.ExecutionContext;

/**
 * Reference {@link RagRetriever} that delegates retrieval to a {@link VectorStore}.
 * Ingestion (extract/chunk/embed) and concrete vector clients live in {@code unfurl-foundry}.
 */
public final class VectorStoreRagRetriever implements RagRetriever {
    private final VectorStore vectorStore;

    public VectorStoreRagRetriever(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public RagResult retrieve(RagQuery query, ExecutionContext context) {
        return vectorStore.query(query, context);
    }
}
