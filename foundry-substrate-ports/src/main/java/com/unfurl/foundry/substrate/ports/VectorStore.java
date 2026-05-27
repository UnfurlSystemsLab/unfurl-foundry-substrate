package com.unfurl.foundry.substrate.ports;

import com.unfurl.foundry.substrate.rag.Chunk;
import com.unfurl.foundry.substrate.rag.RagQuery;
import com.unfurl.foundry.substrate.rag.RagResult;
import com.unfurl.substrate.policy.ExecutionContext;

import java.util.List;

public interface VectorStore {
    void upsert(String collection, List<Chunk> chunks, ExecutionContext context);

    RagResult query(RagQuery query, ExecutionContext context);
}
