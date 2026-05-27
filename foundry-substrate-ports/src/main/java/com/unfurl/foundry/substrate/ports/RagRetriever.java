package com.unfurl.foundry.substrate.ports;

import com.unfurl.foundry.substrate.rag.RagQuery;
import com.unfurl.foundry.substrate.rag.RagResult;
import com.unfurl.substrate.policy.ExecutionContext;

public interface RagRetriever {
    RagResult retrieve(RagQuery query, ExecutionContext context);
}
