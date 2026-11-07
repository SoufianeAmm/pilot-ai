package com.flowpilot.documents.internal.persistence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.flowpilot.shared.id.Ids;

/**
 * Database-backed job queue. Any replica can claim a job; SKIP LOCKED guarantees that two
 * replicas never get the same one, and a job whose lease expired (crashed worker) is claimed again.
 */
@Component
public class IngestionJobQueue {

    public static final int MAX_ATTEMPTS = 3;

    private final JdbcClient jdbc;
    private final Clock clock;

    IngestionJobQueue(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Creates the job, or resets the existing one (re-processing of a document). */
    public void enqueue(UUID documentId) {
        OffsetDateTime now = now();
        jdbc.sql("""
                INSERT INTO ingestion_job (id, document_id, status, attempts, next_run_at, created_at, updated_at, version)
                VALUES (:id, :documentId, 'PENDING', 0, :now, :now, :now, 0)
                ON CONFLICT (document_id) DO UPDATE SET
                    status = 'PENDING', attempts = 0, next_run_at = :now,
                    locked_until = NULL, locked_by = NULL, last_error = NULL,
                    updated_at = :now, version = ingestion_job.version + 1
                """)
                .param("id", Ids.newId())
                .param("documentId", documentId)
                .param("now", now)
                .update();
    }

    public Optional<ClaimedJob> claimNext(String workerId, Duration lease) {
        Instant start = clock.instant();
        return jdbc.sql("""
                UPDATE ingestion_job SET
                    status = 'RUNNING', attempts = attempts + 1,
                    locked_by = :worker, locked_until = :until,
                    updated_at = :now, version = version + 1
                WHERE id = (
                    SELECT id FROM ingestion_job
                    WHERE (status = 'PENDING' AND next_run_at <= :now)
                       OR (status = 'RUNNING' AND locked_until < :now)
                    ORDER BY next_run_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1)
                RETURNING id, document_id, attempts
                """)
                .param("worker", workerId)
                .param("until", at(start.plus(lease)))
                .param("now", at(start))
                .query((rs, rowNum) -> new ClaimedJob(
                        rs.getObject("id", UUID.class),
                        rs.getObject("document_id", UUID.class),
                        rs.getInt("attempts")))
                .optional();
    }

    public void complete(UUID jobId) {
        finish(jobId, "DONE", null);
    }

    public void fail(UUID jobId, String error) {
        finish(jobId, "FAILED", error);
    }

    /** Puts the job back in the queue to be tried again after the delay. */
    public void retryLater(UUID jobId, String error, Duration delay) {
        OffsetDateTime now = now();
        jdbc.sql("""
                UPDATE ingestion_job SET status = 'PENDING', next_run_at = :next,
                    locked_by = NULL, locked_until = NULL, last_error = :error,
                    updated_at = :now, version = version + 1
                WHERE id = :id
                """)
                .param("id", jobId)
                .param("next", at(clock.instant().plus(delay)))
                .param("error", truncate(error))
                .param("now", now)
                .update();
    }

    private void finish(UUID jobId, String status, String error) {
        jdbc.sql("""
                UPDATE ingestion_job SET status = :status, locked_by = NULL, locked_until = NULL,
                    last_error = :error, updated_at = :now, version = version + 1
                WHERE id = :id
                """)
                .param("id", jobId)
                .param("status", status)
                .param("error", truncate(error))
                .param("now", now())
                .update();
    }

    private OffsetDateTime now() {
        return at(clock.instant());
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String truncate(String text) {
        return text == null ? null : text.substring(0, Math.min(text.length(), 500));
    }
}
