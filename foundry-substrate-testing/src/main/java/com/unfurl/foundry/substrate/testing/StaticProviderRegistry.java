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
/**
 * class for the Foundry AI substrate surface; documents the StaticProviderRegistry contract used by DCP ports, adapters, or domain code.
 * Inputs and outputs remain defined by the declared fields and methods, with validation kept inside this type where present.
 */
public final class StaticProviderRegistry implements ProviderRegistry {
    private final Map<String, ModelProvider> models = new HashMap<>();
    private final Map<String, EmbeddingProvider> embedders = new HashMap<>();

/**
 * Performs the registerModel operation for this component, translating validated inputs into the domain result expected by callers.
 */
    public StaticProviderRegistry registerModel(String name, ModelProvider provider) {
        models.put(name, provider);
        return this;
    }

/**
 * Performs the registerEmbedder operation for this component, translating validated inputs into the domain result expected by callers.
 */
    public StaticProviderRegistry registerEmbedder(String name, EmbeddingProvider provider) {
        embedders.put(name, provider);
        return this;
    }

/**
 * Performs the hasProvider operation for this component, translating validated inputs into the domain result expected by callers.
 */
    @Override
    public boolean hasProvider(String name, ProviderKind kind, ExecutionContext context) {
        return kind == ProviderKind.LLM ? models.containsKey(name) : embedders.containsKey(name);
    }

/**
 * Performs the resolveModel operation for this component, translating validated inputs into the domain result expected by callers.
 */
    @Override
    public Optional<ModelProvider> resolveModel(String name, ExecutionContext context) {
        return Optional.ofNullable(models.get(name));
    }

/**
 * Performs the resolveEmbedder operation for this component, translating validated inputs into the domain result expected by callers.
 */
    @Override
    public Optional<EmbeddingProvider> resolveEmbedder(String name, ExecutionContext context) {
        return Optional.ofNullable(embedders.get(name));
    }
}
