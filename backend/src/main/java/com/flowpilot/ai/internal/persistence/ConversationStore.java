package com.flowpilot.ai.internal.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.flowpilot.projects.ProjectScope;
import com.flowpilot.shared.id.Ids;

/**
 * Conversations and messages of the AI analyst. Every query is scoped by organization, project
 * AND user: a conversation is private to the person who started it (backlog D5), so even a project
 * manager cannot read someone else's questions.
 */
@Component
public class ConversationStore {

    public record Conversation(UUID id, UUID projectId, String title, Instant createdAt, Instant updatedAt) {
    }

    public record StoredMessage(UUID id, String role, String content, String structuredJson, String verificationJson,
                                String status, String model, Instant createdAt) {
    }

    /** One finished question and its answer (or failure), written in one transaction. */
    public record Exchange(String question, Instant askedAt, String answerContent, String structuredJson,
                           String verificationJson, String status, String model, Integer promptTokens,
                           Integer completionTokens, Integer latencyMs, ToolCall toolCall) {
    }

    /** A tool the analyst ran for the answer (ai_tool_invocation), with the references it returned. */
    public record ToolCall(String name, String argumentsJson, String returnedRefsJson, int durationMs) {
    }

    private static final String COLUMNS = "id, project_id, title, created_at, updated_at";

    private final JdbcClient jdbc;
    private final Clock clock;

    ConversationStore(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Conversation create(ProjectScope scope, String title) {
        UUID id = Ids.newId();
        OffsetDateTime now = utc(clock.instant());
        jdbc.sql("""
                INSERT INTO ai_conversation (id, org_id, project_id, user_id, title, created_at, updated_at, version)
                VALUES (:id, :orgId, :projectId, :userId, :title, :now, :now, 0)
                """)
                .param("id", id).param("orgId", scope.orgId()).param("projectId", scope.projectId())
                .param("userId", scope.userId()).param("title", title).param("now", now)
                .update();
        return new Conversation(id, scope.projectId(), title, now.toInstant(), now.toInstant());
    }

    public List<Conversation> list(ProjectScope scope, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM ai_conversation
                WHERE org_id = :orgId AND project_id = :projectId AND user_id = :userId
                ORDER BY updated_at DESC
                LIMIT :limit
                """)
                .param("orgId", scope.orgId()).param("projectId", scope.projectId())
                .param("userId", scope.userId()).param("limit", limit)
                .query(ConversationStore::conversation)
                .list();
    }

    public Optional<Conversation> find(ProjectScope scope, UUID conversationId) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM ai_conversation
                WHERE id = :id AND org_id = :orgId AND project_id = :projectId AND user_id = :userId
                """)
                .param("id", conversationId).param("orgId", scope.orgId())
                .param("projectId", scope.projectId()).param("userId", scope.userId())
                .query(ConversationStore::conversation)
                .optional();
    }

    /** @return whether a conversation of this user was deleted (its messages and tool calls cascade) */
    public boolean delete(ProjectScope scope, UUID conversationId) {
        return jdbc.sql("""
                DELETE FROM ai_conversation
                WHERE id = :id AND org_id = :orgId AND project_id = :projectId AND user_id = :userId
                """)
                .param("id", conversationId).param("orgId", scope.orgId())
                .param("projectId", scope.projectId()).param("userId", scope.userId())
                .update() > 0;
    }

    /** Messages of a conversation the caller has already loaded through {@link #find}, oldest first. */
    public List<StoredMessage> messages(ProjectScope scope, UUID conversationId) {
        return jdbc.sql("""
                SELECT id, role, content, structured_response::text AS structured, verification::text AS verification,
                       status, model, created_at
                FROM ai_message
                WHERE conversation_id = :conversationId AND org_id = :orgId
                ORDER BY created_at, CASE role WHEN 'USER' THEN 0 ELSE 1 END
                """)
                .param("conversationId", conversationId).param("orgId", scope.orgId())
                .query(ConversationStore::message)
                .list();
    }

    /** Stores the question, the answer and the tool call atomically, and bumps the conversation. */
    @Transactional
    public StoredMessage saveExchange(ProjectScope scope, UUID conversationId, Exchange exchange) {
        OffsetDateTime askedAt = utc(exchange.askedAt());
        OffsetDateTime answeredAt = utc(clock.instant());
        if (!answeredAt.isAfter(askedAt)) {
            answeredAt = askedAt.plusNanos(1_000);
        }
        insertMessage(scope, Ids.newId(), conversationId, "USER", exchange.question(), null, null, "OK", null,
                null, null, null, askedAt);
        UUID answerId = Ids.newId();
        insertMessage(scope, answerId, conversationId, "ASSISTANT", exchange.answerContent(), exchange.structuredJson(),
                exchange.verificationJson(), exchange.status(), exchange.model(), exchange.promptTokens(),
                exchange.completionTokens(), exchange.latencyMs(), answeredAt);
        if (exchange.toolCall() != null) {
            ToolCall tool = exchange.toolCall();
            jdbc.sql("""
                    INSERT INTO ai_tool_invocation (id, org_id, message_id, tool_name, arguments, returned_refs,
                                                    authorized, duration_ms, created_at)
                    VALUES (:id, :orgId, :messageId, :name, CAST(:arguments AS jsonb), CAST(:refs AS jsonb),
                            true, :durationMs, :now)
                    """)
                    .param("id", Ids.newId()).param("orgId", scope.orgId()).param("messageId", answerId)
                    .param("name", tool.name()).param("arguments", tool.argumentsJson())
                    .param("refs", tool.returnedRefsJson()).param("durationMs", tool.durationMs())
                    .param("now", answeredAt)
                    .update();
        }
        jdbc.sql("UPDATE ai_conversation SET updated_at = :now, version = version + 1 WHERE id = :id AND org_id = :orgId")
                .param("now", answeredAt).param("id", conversationId).param("orgId", scope.orgId())
                .update();
        return new StoredMessage(answerId, "ASSISTANT", exchange.answerContent(), exchange.structuredJson(),
                exchange.verificationJson(), exchange.status(), exchange.model(), answeredAt.toInstant());
    }

    private void insertMessage(ProjectScope scope, UUID id, UUID conversationId, String role, String content,
                               String structuredJson, String verificationJson, String status, String model,
                               Integer promptTokens, Integer completionTokens, Integer latencyMs, OffsetDateTime at) {
        jdbc.sql("""
                INSERT INTO ai_message (id, org_id, conversation_id, role, content, structured_response, status, model,
                                        prompt_tokens, completion_tokens, latency_ms, verification, created_at)
                VALUES (:id, :orgId, :conversationId, :role, :content, CAST(:structured AS jsonb), :status, :model,
                        :promptTokens, :completionTokens, :latencyMs, CAST(:verification AS jsonb), :at)
                """)
                .param("id", id).param("orgId", scope.orgId()).param("conversationId", conversationId)
                .param("role", role).param("content", content).param("structured", structuredJson)
                .param("status", status).param("model", model).param("promptTokens", promptTokens)
                .param("completionTokens", completionTokens).param("latencyMs", latencyMs)
                .param("verification", verificationJson == null ? "{}" : verificationJson).param("at", at)
                .update();
    }

    private static Conversation conversation(ResultSet rs, int row) throws SQLException {
        return new Conversation(rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class),
                rs.getString("title"), rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private static StoredMessage message(ResultSet rs, int row) throws SQLException {
        return new StoredMessage(rs.getObject("id", UUID.class), rs.getString("role"), rs.getString("content"),
                rs.getString("structured"), rs.getString("verification"), rs.getString("status"),
                rs.getString("model"), rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
