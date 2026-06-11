package com.unfurl.foundry.substrate.springai;

import com.unfurl.foundry.substrate.rag.Chunk;
import com.unfurl.foundry.substrate.rag.ChunkSource;
import com.unfurl.foundry.substrate.rag.RagQuery;
import com.unfurl.foundry.substrate.rag.RagResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiVectorStoreAdapterTest {

    @Test
    void upsertProjectsChunksOntoSpringDocumentsAndCarriesCollectionInMetadata() {
        AtomicReference<List<Document>> captured = new AtomicReference<>();
        VectorStore springStore = new RecordingVectorStore(captured::set);
        SpringAiVectorStoreAdapter adapter = new SpringAiVectorStoreAdapter(springStore);

        Chunk chunk = new Chunk(
                "c1",
                "alpha",
                0,
                new ChunkSource("doc-1", "file://x.txt", "p1", Map.of()),
                Map.of("topic", "demo"));

        adapter.upsert("knowledge", List.of(chunk), null);

        List<Document> documents = captured.get();
        assertThat(documents).hasSize(1);
        Document doc = documents.get(0);
        assertThat(doc.getId()).isEqualTo("c1");
        assertThat(doc.getText()).isEqualTo("alpha");
        assertThat(doc.getMetadata())
                .containsEntry(SpringAiVectorStoreAdapter.COLLECTION_METADATA_KEY, "knowledge")
                .containsEntry("documentId", "doc-1")
                .containsEntry("uri", "file://x.txt")
                .containsEntry("location", "p1")
                .containsEntry("topic", "demo");
    }

    @Test
    void queryProjectsSearchHitsBackIntoRagChunksRestoringSourceFields() {
        VectorStore springStore = new RecordingVectorStore(ignored -> {}) {
            @Override
            public List<Document> doSimilaritySearch(SearchRequest request) {
                Document doc = Document.builder()
                        .id("hit-1")
                        .text("relevant passage")
                        .metadata(Map.of(
                                "documentId", "doc-7",
                                "uri", "s3://bucket/key",
                                "location", "p3",
                                "topic", "demo"))
                        .score(0.87)
                        .build();
                return List.of(doc);
            }
        };
        SpringAiVectorStoreAdapter adapter = new SpringAiVectorStoreAdapter(springStore);

        RagResult result = adapter.query(
                new RagQuery("find me one", 3, Map.of(), "knowledge", Map.of()),
                null);

        assertThat(result.chunks()).hasSize(1);
        Chunk chunk = result.chunks().get(0);
        assertThat(chunk.id()).isEqualTo("hit-1");
        assertThat(chunk.text()).isEqualTo("relevant passage");
        assertThat(chunk.score()).isEqualTo(0.87);
        assertThat(chunk.source().documentId()).isEqualTo("doc-7");
        assertThat(chunk.source().uri()).isEqualTo("s3://bucket/key");
        assertThat(chunk.source().location()).isEqualTo("p3");
        // documentId / uri / location should be stripped from chunk
        // metadata since they were lifted into ChunkSource.
        assertThat(chunk.metadata()).containsEntry("topic", "demo");
        assertThat(chunk.metadata()).doesNotContainKeys("documentId", "uri", "location");
    }

    @Test
    void emptyHitsProduceEmptyResult() {
        VectorStore springStore = new RecordingVectorStore(ignored -> {}) {
            @Override
            public List<Document> doSimilaritySearch(SearchRequest request) {
                return List.of();
            }
        };
        SpringAiVectorStoreAdapter adapter = new SpringAiVectorStoreAdapter(springStore);

        RagResult result = adapter.query(
                new RagQuery("nothing", 5, Map.of(), "c", Map.of()),
                null);

        assertThat(result.chunks()).isEmpty();
    }

    /**
     * Minimal Spring AI {@link VectorStore} stub that records the
     * documents passed to {@link #add(List)} and lets tests override
     * {@link #doSimilaritySearch(SearchRequest)} to return canned hits.
     * Spring AI 1.0 ships {@code VectorStore} as a SAM-ish interface
     * with default methods around the underlying {@code doAdd} /
     * {@code doSimilaritySearch}; we implement the minimum surface
     * needed for the adapter's tests.
     */
    private static class RecordingVectorStore implements VectorStore {
        private final java.util.function.Consumer<List<Document>> onAdd;

        RecordingVectorStore(java.util.function.Consumer<List<Document>> onAdd) {
            this.onAdd = onAdd;
        }

        @Override
        public void add(List<Document> documents) {
            onAdd.accept(new ArrayList<>(documents));
        }

        @Override
        public void delete(List<String> idList) {
        }

        @Override
        public void delete(org.springframework.ai.vectorstore.filter.Filter.Expression filterExpression) {
        }

        @Override
        public List<Document> similaritySearch(String query) {
            return List.of();
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            return doSimilaritySearch(request);
        }

        protected List<Document> doSimilaritySearch(SearchRequest request) {
            return List.of();
        }

        @Override
        public <T> Optional<T> getNativeClient() {
            return Optional.empty();
        }
    }
}
