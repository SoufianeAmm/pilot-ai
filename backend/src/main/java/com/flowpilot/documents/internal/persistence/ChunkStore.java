package com.flowpilot.documents.internal.persistence;

import java.sql.Types;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.flowpilot.shared.id.Ids;

/** Reads and writes document_chunk with plain SQL (the pgvector column is not mapped to JPA). */
@Component
public class ChunkStore {

    public record NewChunk(int index, Integer pageFrom, Integer pageTo, String sectionTitle,
                           String content, int tokenCount, float[] embedding) {
    }

    private final JdbcClient jdbc;

    ChunkStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Replaces every chunk of the document, so re-processing never leaves duplicates. */
    @Transactional
    public void replaceAll(UUID orgId, UUID projectId, UUID documentId, String embeddingModel,
                           List<NewChunk> chunks) {
        deleteByDocument(documentId);
        for (NewChunk chunk : chunks) {
            jdbc.sql("""
                    INSERT INTO document_chunk (id, org_id, project_id, document_id, chunk_index,
                        page_from, page_to, section_title, content, token_count, embedding, embedding_model)
                    VALUES (:id, :orgId, :projectId, :documentId, :index,
                        :pageFrom, :pageTo, :sectionTitle, :content, :tokenCount,
                        CAST(:embedding AS extensions.vector), :model)
                    """)
                    .param("id", Ids.newId())
                    .param("orgId", orgId)
                    .param("projectId", projectId)
                    .param("documentId", documentId)
                    .param("index", chunk.index())
                    .param("pageFrom", chunk.pageFrom(), Types.INTEGER)
                    .param("pageTo", chunk.pageTo(), Types.INTEGER)
                    .param("sectionTitle", chunk.sectionTitle(), Types.VARCHAR)
                    .param("content", chunk.content())
                    .param("tokenCount", chunk.tokenCount())
                    .param("embedding", toVectorLiteral(chunk.embedding()))
                    .param("model", embeddingModel)
                    .update();
        }
    }

    @Transactional
    public void deleteByDocument(UUID documentId) {
        jdbc.sql("DELETE FROM document_chunk WHERE document_id = :id")
                .param("id", documentId)
                .update();
    }

    /** pgvector text format: [0.1,0.2,0.3] */
    static String toVectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder(vector.length * 8).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        return builder.append(']').toString();
    }
}
