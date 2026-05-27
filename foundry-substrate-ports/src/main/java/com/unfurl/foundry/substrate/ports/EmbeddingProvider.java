package com.unfurl.foundry.substrate.ports;

import com.unfurl.foundry.substrate.embedding.EmbeddingRequest;
import com.unfurl.foundry.substrate.embedding.EmbeddingResult;
import com.unfurl.substrate.policy.ExecutionContext;

public interface EmbeddingProvider {
    EmbeddingResult embed(EmbeddingRequest request, ExecutionContext context);
}
