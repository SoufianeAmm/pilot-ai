package com.flowpilot.documents.internal.ingestion;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.flowpilot.documents.internal.persistence.ClaimedJob;
import com.flowpilot.documents.internal.persistence.IngestionJobQueue;

/**
 * Polls the job queue. Runs on every replica (SKIP LOCKED keeps them from clashing), never in the
 * one-shot "migrate" mode. A single scheduler thread means at most one document per replica at a time.
 */
@Component
@Profile("!migrate")
class IngestionWorker {

    private static final Logger log = LoggerFactory.getLogger(IngestionWorker.class);
    private static final Duration LEASE = Duration.ofMinutes(5);
    private static final Duration BASE_RETRY_DELAY = Duration.ofSeconds(30);
    private static final String GENERIC_FAILURE = "Processing failed after several attempts. Please try again later.";

    private final IngestionJobQueue jobs;
    private final IngestionService ingestion;
    private final String workerId;

    IngestionWorker(IngestionJobQueue jobs, IngestionService ingestion) {
        this.jobs = jobs;
        this.ingestion = ingestion;
        String host = System.getenv("HOSTNAME");
        this.workerId = (host == null ? "local" : host) + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Scheduled(fixedDelay = 2000)
    void poll() {
        while (processNext()) {
            // keep going while there are jobs waiting
        }
    }

    private boolean processNext() {
        Optional<ClaimedJob> claimed;
        try {
            claimed = jobs.claimNext(workerId, LEASE);
        } catch (RuntimeException e) {
            log.warn("Could not claim an ingestion job: {}", e.getClass().getSimpleName());
            return false;
        }
        if (claimed.isEmpty()) {
            return false;
        }
        ClaimedJob job = claimed.get();
        try {
            if (job.attempts() > IngestionJobQueue.MAX_ATTEMPTS) {
                fail(job, GENERIC_FAILURE, "Gave up: the lease expired too many times.");
                return true;
            }
            ingestion.process(job.documentId());
            jobs.complete(job.jobId());
            log.info("Ingested document {} (attempt {})", job.documentId(), job.attempts());
        } catch (PermanentIngestionException e) {
            fail(job, e.getMessage(), e.getMessage());
        } catch (Exception e) {
            String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
            if (job.attempts() >= IngestionJobQueue.MAX_ATTEMPTS) {
                fail(job, GENERIC_FAILURE, reason);
            } else {
                Duration delay = BASE_RETRY_DELAY.multipliedBy(1L << (job.attempts() - 1));
                log.warn("Ingestion of document {} failed (attempt {}), retrying in {}: {}",
                        job.documentId(), job.attempts(), delay, reason);
                jobs.retryLater(job.jobId(), reason, delay);
            }
        }
        return true;
    }

    private void fail(ClaimedJob job, String userMessage, String internalReason) {
        log.warn("Ingestion of document {} failed for good: {}", job.documentId(), internalReason);
        ingestion.markFailed(job.documentId(), userMessage);
        jobs.fail(job.jobId(), internalReason);
    }
}