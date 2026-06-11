package com.unfurl.foundry.substrate.springai;

import com.unfurl.foundry.substrate.embedding.EmbeddingRequest;
import com.unfurl.foundry.substrate.embedding.EmbeddingResult;
import com.unfurl.foundry.substrate.model.ModelUsage;
import com.unfurl.foundry.substrate.ports.EmbeddingProvider;
import com.unfurl.substrate.policy.ExecutionContext;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Foundry {@link EmbeddingProvider} backed by a host-supplied Spring AI
 * {@link EmbeddingModel}. Wraps the model's batch-embed call and projects
 * the {@link EmbeddingResponse} into foundry's neutral
 * {@link EmbeddingResult}.
 *
 * <p>Same plug-and-play property as {@link SpringAiModelProvider}: the
 * customer wires their Spring AI embedding starter (OpenAI / Azure /
 * Ollama / Bedrock / ...) and this adapter routes through whatever bean
 * is present, so foundry substrates that consume EmbeddingProvider
 * never name a provider.
 */
public final class SpringAiEmbeddingProvider implements EmbeddingProvider {
    private final EmbeddingModel embeddingModel;

    public SpringAiEmbeddingProvider(EmbeddingModel embeddingModel) {
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel is required");
    }

    @Override
    public EmbeddingResult embed(EmbeddingRequest request, ExecutionContext context) {
        EmbeddingResponse response = embeddingModel.embedForResponse(request.inputs());
        List<float[]> vectors = new ArrayList<>(response.getResults().size());
        for (Embedding result : response.getResults()) {
            vectors.add(result.getOutput());
        }
        return new EmbeddingResult(vectors, usageFrom(response), Map.of());
    }

    private ModelUsage usageFrom(EmbeddingResponse response) {
        if (response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return ModelUsage.zero();
        }
        var usage = response.getMetadata().getUsage();
        Integer prompt = usage.getPromptTokens();
        Integer completion = usage.getCompletionTokens();
        return new ModelUsage(
                prompt == null ? 0 : prompt.longValue(),
                completion == null ? 0 : completion.longValue());
    }
}
