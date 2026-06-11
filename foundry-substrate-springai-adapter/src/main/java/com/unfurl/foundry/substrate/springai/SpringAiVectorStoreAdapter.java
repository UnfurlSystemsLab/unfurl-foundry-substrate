package com.unfurl.foundry.substrate.springai;

import com.unfurl.foundry.substrate.ports.VectorStore;
import com.unfurl.foundry.substrate.rag.Chunk;
import com.unfurl.foundry.substrate.rag.ChunkSource;
import com.unfurl.foundry.substrate.rag.RagQuery;
import com.unfurl.foundry.substrate.rag.RagResult;
import com.unfurl.substrate.policy.ExecutionContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Foundry {@link VectorStore} backed by a host-supplied Spring AI
 * {@link org.springframework.ai.vectorstore.VectorStore}. Maps foundry's
 * {@link Chunk} and {@link RagQuery} types onto Spring AI's
 * {@link Document} and {@link SearchRequest}, then projects the
 * similarity-search results back into a {@link RagResult}.
 *
 * <p>The customer's Spring context configures the concrete backend —
 * Chroma, PGVector, Pinecone, Weaviate, Qdrant, Redis, ... — and this
 * adapter speaks foundry's neutral RAG API regardless of which one is
 * wired. The {@code collection} parameter on {@link #upsert} is stored
 * on the document as metadata so multi-collection backends can route it
 * (Spring AI's VectorStore API itself doesn't expose collection
 * directly; per-collection backends typically use one bean instance per
 * collection or rely on metadata-based filtering).
 */
public final class SpringAiVectorStoreAdapter implements VectorStore {
    /** Metadata key under which the foundry collection ref is stored. */
    public static final String COLLECTION_METADATA_KEY = "foundry.collection";

    private final org.springframework.ai.vectorstore.VectorStore springStore;

    public SpringAiVectorStoreAdapter(org.springframework.ai.vectorstore.VectorStore springStore) {
        this.springStore = Objects.requireNonNull(springStore, "springStore is required");
    }

    @Override
    public void upsert(String collection, List<Chunk> chunks, ExecutionContext context) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        List<Document> documents = new ArrayList<>(chunks.size());
        for (Chunk chunk : chunks) {
            Map<String, Object> metadata = new LinkedHashMap<>(chunk.metadata());
            if (collection != null && !collection.isBlank()) {
                metadata.put(COLLECTION_METADATA_KEY, collection);
            }
            if (chunk.source() != null) {
                if (chunk.source().documentId() != null) {
                    metadata.put("documentId", chunk.source().documentId());
                }
                if (chunk.source().uri() != null) {
                    metadata.put("uri", chunk.source().uri());
                }
                if (chunk.source().location() != null) {
                    metadata.put("location", chunk.source().location());
                }
            }
            String id = chunk.id() == null || chunk.id().isBlank()
                    ? java.util.UUID.randomUUID().toString()
                    : chunk.id();
            documents.add(Document.builder()
                    .id(id)
                    .text(chunk.text() == null ? "" : chunk.text())
                    .metadata(metadata)
                    .build());
        }
        springStore.add(documents);
    }

    @Override
    public RagResult query(RagQuery query, ExecutionContext context) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query.query() == null ? "" : query.query())
                .topK(Math.max(1, query.topK()));
        // Foundry's RagQuery filters travel as a Map<String,Object>. We
        // do not synthesise a FilterExpression for them here — the
        // expression DSL is provider-specific and foundry's filter map
        // is intentionally untyped. A later commit can map known shapes
        // (e.g. {key: value}) into FilterExpressionBuilder.
        List<Document> documents = springStore.similaritySearch(builder.build());
        if (documents == null || documents.isEmpty()) {
            return RagResult.empty();
        }
        List<Chunk> chunks = new ArrayList<>(documents.size());
        for (Document document : documents) {
            chunks.add(toChunk(document));
        }
        return new RagResult(chunks, Map.of());
    }

    private Chunk toChunk(Document document) {
        Map<String, Object> metadata = document.getMetadata() == null
                ? Map.of()
                : new HashMap<>(document.getMetadata());
        ChunkSource source = new ChunkSource(
                stringOrNull(metadata.remove("documentId")),
                stringOrNull(metadata.remove("uri")),
                stringOrNull(metadata.remove("location")),
                Map.of());
        Double score = document.getScore();
        return new Chunk(
                document.getId(),
                Objects.requireNonNullElse(document.getText(), ""),
                score == null ? 0.0 : score,
                source,
                Map.copyOf(metadata));
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
