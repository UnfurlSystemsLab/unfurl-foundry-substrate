package com.unfurl.foundry.substrate.springai;

import com.unfurl.foundry.substrate.embedding.EmbeddingRequest;
import com.unfurl.foundry.substrate.embedding.EmbeddingResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiEmbeddingProviderTest {

    @Test
    void returnsVectorsForEachInputInOrder() {
        EmbeddingModel embeddingModel = new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(org.springframework.ai.embedding.EmbeddingRequest request) {
                List<Embedding> embeddings = List.of(
                        new Embedding(new float[]{0.1f, 0.2f}, 0),
                        new Embedding(new float[]{0.3f, 0.4f}, 1));
                return new EmbeddingResponse(embeddings, new EmbeddingResponseMetadata());
            }

            @Override
            public float[] embed(Document document) {
                return new float[]{0};
            }
        };
        SpringAiEmbeddingProvider provider = new SpringAiEmbeddingProvider(embeddingModel);

        EmbeddingResult result = provider.embed(
                new EmbeddingRequest(
                        List.of("hello", "world"),
                        "embed-model",
                        Map.of()),
                null);

        assertThat(result.vectors()).hasSize(2);
        assertThat(result.vectors().get(0)).containsExactly(0.1f, 0.2f);
        assertThat(result.vectors().get(1)).containsExactly(0.3f, 0.4f);
    }

    @Test
    void emptyInputProducesEmptyVectorList() {
        EmbeddingModel embeddingModel = new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(org.springframework.ai.embedding.EmbeddingRequest request) {
                return new EmbeddingResponse(List.of(), new EmbeddingResponseMetadata());
            }

            @Override
            public float[] embed(Document document) {
                return new float[]{0};
            }
        };
        SpringAiEmbeddingProvider provider = new SpringAiEmbeddingProvider(embeddingModel);

        EmbeddingResult result = provider.embed(
                new EmbeddingRequest(List.of(), "embed-model", Map.of()),
                null);

        assertThat(result.vectors()).isEmpty();
    }
}
