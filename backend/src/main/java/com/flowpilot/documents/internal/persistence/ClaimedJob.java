package com.flowpilot.documents.internal.persistence;

import java.util.UUID;

public record ClaimedJob(UUID jobId, UUID documentId, int attempts) {
}