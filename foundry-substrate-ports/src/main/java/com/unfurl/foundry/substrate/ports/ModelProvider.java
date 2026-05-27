package com.unfurl.foundry.substrate.ports;

import com.unfurl.foundry.substrate.model.ModelRequest;
import com.unfurl.foundry.substrate.model.ModelResponse;
import com.unfurl.substrate.policy.ExecutionContext;

/**
 * Port for invoking a chat/completion model. Concrete provider adapters (Anthropic,
 * OpenAI, Azure, Ollama, …) live above the substrate; this layer never imports a model SDK.
 * Runtime invocation uses the customer's own configured provider.
 */
public interface ModelProvider {
    ModelResponse complete(ModelRequest request, ExecutionContext context);
}
