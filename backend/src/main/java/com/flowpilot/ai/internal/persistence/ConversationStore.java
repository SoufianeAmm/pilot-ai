package com.flowpilot.ai.internal.persistence;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.flowpilot.shared.id.Ids;

/** Conversations and messages of the AI assistant. */
@Component
public class ConversationStore {

    public record Conversation(UUID id, UUID projectId, String title,
                               OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record Message(UUID id, UUID conversationId, String role, String content,
                          String structuredResponse, String status, String model,
                          OffsetDateTime createdAt) {
    }

    private final JdbcClient jdbc;
    private final Clock clock;

    ConversationStore(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Conversation create(UUID orgId, UUID projectId, UUID userId, String title) {
        UUID id = Ids.newId();
        OffsetDateTime now = now();
        jdbc.sql("""
                INSERT INTO ai_conversation (id, org_id, project_id, user_id, title, created_at, updated_at, version)
                VALUES (:id, :orgId, :projectId, :userId, :title, :now, :now, 0)
                """)
                .param("id", id)
                .param("orgId", orgId)
                .param("projectId", projectId)
                .param("userId", userId)
                .param("title", title)
                .param("now", now)
                .update();
        return new Conversation(id, projectId, title, now, now);
    }

    /** Only the conversations of this user: nobody reads someone else's. */
    public List<Conversation> list(UUID projectId, UUID userId) {
        return jdbc.sql("""
                SELECT id, project_id, title, created_at, updated_at FROM ai_conversation
                WHERE project_id = :projectId AND user_id = :userId
                ORDER BY updated_at DESC
                LIMIT 100
                """)
                .param("projectId", projectId)
                .param("userId", userId)
                .query((rs, n) -> new Conversation(
                        rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class),
                        rs.getString("title"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class)))
                .list();
    }

    public Optional<Conversation> find(UUID projectId, UUID userId, UUID conversationId) {
        return jdbc.sql("""
                SELECT id, project_id, title, created_at, updated_at FROM ai_conversation
                WHERE id = :id AND project_id = :projectId AND user_id = :userId
                """)
                .param("id", conversationId)
                .param("projectId", projectId)
                .param("userId", userId)
                .query((rs, n) -> new Conversation(
                        rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class),
                        rs.getString("title"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class)))
                .optional();
    }

    public Message addMessage(UUID orgId, UUID conversationId, String role, String content,
                              String structuredResponseJson, String status, String model,
                              Integer promptTokens, Integer completionTokens, Integer latencyMs,
                              String verificationJson) {
        UUID id = Ids.newId();
        OffsetDateTime now = now();
        jdbc.sql("""
                INSERT INTO ai_message (id, org_id, conversation_id, role, content, structured_response,
                                        status, model, prompt_tokens, completion_tokens, latency_ms,
                                        verification, created_at)
                VALUES (:id, :orgId, :conversationId, :role, :content, CAST(:structured AS jsonb),
                        :status, :model, :promptTokens, :completionTokens, :latencyMs,
                        CAST(:verification AS jsonb), :now)
                """)
                .param("id", id)
                .param("orgId", orgId)
                .param("conversationId", conversationId)
                .param("role", role)
                .param("content", content)
                .param("structured", structuredResponseJson)
                .param("status", status)
                .param("model", model)
                .param("promptTokens", promptTokens)
                .param("completionTokens", completionTokens)
                .param("latencyMs", latencyMs)
                .param("verification", verificationJson == null ? "{}" : verificationJson)
                .param("now", now)
                .update();
        jdbc.sql("UPDATE ai_conversation SET updated_at = :now, version = version + 1 WHERE id = :id")
                .param("now", now)
                .param("id", conversationId)
                .update();
        return new Message(id, conversationId, role, content, structuredResponseJson, status, model, now);
    }

    public List<Message> messages(UUID conversationId) {
        return jdbc.sql("""
                SELECT id, conversation_id, role, content, structured_response::text AS structured,
                       status, model, created_at
                FROM ai_message WHERE conversation_id = :conversationId
                ORDER BY created_at, id
                """)
                .param("conversationId", conversationId)
                .query((rs, n) -> new Message(
                        rs.getObject("id", UUID.class), rs.getObject("conversation_id", UUID.class),
                        rs.getString("role"), rs.getString("content"), rs.getString("structured"),
                        rs.getString("status"), rs.getString("model"),
                        rs.getObject("created_at", OffsetDateTime.class)))
                .list();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}