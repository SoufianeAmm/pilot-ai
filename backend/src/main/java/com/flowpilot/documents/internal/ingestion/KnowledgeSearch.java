package com.flowpilot.documents.internal.ingestion;

import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Semantic search over the passages of one project. Every result carries its source (document + pages). */
@Component
public class KnowledgeSearch {

    public static final int MAX_RESULTS = 20;

    /** A passage found by the search, with what is needed to cite it. */
    public record Passage(UUID chunkId, UUID documentId, String filename, int chunkIndex,
                          Integer pageFrom, Integer pageTo, String content, double score) {
    }

    private final EmbeddingClient embeddings;
    private final JdbcClient jdbc;

    KnowledgeSearch(EmbeddingClient embeddings, JdbcClient jdbc) {
        this.embeddings = embeddings;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Passage> search(UUID projectId, String question, int limit) {
        int top = Math.max(1, Math.min(limit, MAX_RESULTS));
        String vector = toVectorLiteral(embeddings.embed(List.of(question)).get(0));

        // Lets the index keep scanning when the project/deleted filters remove many candidates.
        jdbc.sql("SET LOCAL hnsw.iterative_scan = relaxed_order").update();

        return jdbc.sql("""
                SELECT c.id, c.document_id, d.filename, c.chunk_index, c.page_from, c.page_to, c.content,
                       1 - (c.embedding OPERATOR(extensions.<=>) CAST(:query AS extensions.vector)) AS score
                FROM document_chunk c
                JOIN document d ON d.id = c.document_id
                WHERE c.project_id = :projectId AND d.deleted_at IS NULL
                ORDER BY c.embedding OPERATOR(extensions.<=>) CAST(:query AS extensions.vector)
                LIMIT :limit
                """)
                .param("query", vector)
                .param("projectId", projectId)
                .param("limit", top)
                .query((rs, rowNum) -> new Passage(
                        rs.getObject("id", UUID.class),
                        rs.getObject("document_id", UUID.class),
                        rs.getString("filename"),
                        rs.getInt("chunk_index"),
                        (Integer) rs.getObject("page_from"),
                        (Integer) rs.getObject("page_to"),
                        rs.getString("content"),
                        rs.getDouble("score")))
                .list();
    }

    private static String toVectorLiteral(float[] vector) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float value : vector) {
            joiner.add(Float.toString(value));
        }
        return joiner.toString();
    }
}