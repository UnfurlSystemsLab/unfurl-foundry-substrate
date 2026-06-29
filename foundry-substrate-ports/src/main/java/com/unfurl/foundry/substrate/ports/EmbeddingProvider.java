package com.unfurl.foundry.substrate.ports;

import com.unfurl.foundry.substrate.embedding.EmbeddingRequest;
import com.unfurl.foundry.substrate.embedding.EmbeddingResult;
import com.unfurl.substrate.policy.ExecutionContext;

/**
 * interface for the Foundry AI substrate surface; documents the EmbeddingProvider contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public interface EmbeddingProvider {
    EmbeddingResult embed(EmbeddingRequest request, ExecutionContext context);
}
