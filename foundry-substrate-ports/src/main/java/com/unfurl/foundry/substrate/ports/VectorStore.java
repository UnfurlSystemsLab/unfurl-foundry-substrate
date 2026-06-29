package com.unfurl.foundry.substrate.ports;

import com.unfurl.foundry.substrate.rag.Chunk;
import com.unfurl.foundry.substrate.rag.RagQuery;
import com.unfurl.foundry.substrate.rag.RagResult;
import com.unfurl.substrate.policy.ExecutionContext;

import java.util.List;

/**
 * interface for the Foundry AI substrate surface; documents the VectorStore contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public interface VectorStore {
    void upsert(String collection, List<Chunk> chunks, ExecutionContext context);

    RagResult query(RagQuery query, ExecutionContext context);
}
