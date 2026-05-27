package com.unfurl.foundry.substrate.ports;

import com.unfurl.substrate.policy.ExecutionContext;

import java.util.Optional;

/**
 * Tenant-scoped resolution of model/embedder providers by logical name. This is a
 * <em>port</em>: the per-tenant store, credential encryption, and concrete adapters are
 * implemented in {@code unfurl-foundry}. The substrate owns the resolution shape only.
 */
public interface ProviderRegistry {
    boolean hasProvider(String name, ProviderKind kind, ExecutionContext context);

    Optional<ModelProvider> resolveModel(String name, ExecutionContext context);

    Optional<EmbeddingProvider> resolveEmbedder(String name, ExecutionContext context);
}
