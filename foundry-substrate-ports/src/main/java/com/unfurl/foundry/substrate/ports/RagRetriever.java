package com.unfurl.foundry.substrate.ports;

import com.unfurl.foundry.substrate.rag.RagQuery;
import com.unfurl.foundry.substrate.rag.RagResult;
import com.unfurl.substrate.policy.ExecutionContext;

/**
 * interface for the Foundry AI substrate surface; documents the RagRetriever contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public interface RagRetriever {
    RagResult retrieve(RagQuery query, ExecutionContext context);
}
