package com.unfurl.foundry.substrate.testing;

import com.unfurl.foundry.substrate.ports.EmbeddingProvider;
import com.unfurl.foundry.substrate.ports.ModelProvider;
import com.unfurl.foundry.substrate.ports.ProviderKind;
import com.unfurl.foundry.substrate.ports.ProviderRegistry;
import com.unfurl.substrate.policy.ExecutionContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory provider registry for tests, keyed by logical name. */
public final class StaticProviderRegistry implements ProviderRegistry {
    private final Map<String, ModelProvider> models = new HashMap<>();
    private final Map<String, EmbeddingProvider> embedders = new HashMap<>();

    public StaticProviderRegistry registerModel(String name, ModelProvider provider) {
        models.put(name, provider);
        return this;
    }

    public StaticProviderRegistry registerEmbedder(String name, EmbeddingProvider provider) {
        embedders.put(name, provider);
        return this;
    }

    @Override
    public boolean hasProvider(String name, ProviderKind kind, ExecutionContext context) {
        return kind == ProviderKind.LLM ? models.containsKey(name) : embedders.containsKey(name);
    }

    @Override
    public Optional<ModelProvider> resolveModel(String name, ExecutionContext context) {
        return Optional.ofNullable(models.get(name));
    }

    @Override
    public Optional<EmbeddingProvider> resolveEmbedder(String name, ExecutionContext context) {
        return Optional.ofNullable(embedders.get(name));
    }
}
