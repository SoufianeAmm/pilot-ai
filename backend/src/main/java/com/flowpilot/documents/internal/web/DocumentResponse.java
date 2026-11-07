package com.flowpilot.documents.internal.web;

import java.time.Instant;
import java.util.UUID;

import com.flowpilot.documents.internal.domain.Document;

public record DocumentResponse(
        UUID id,
        UUID projectId,
        String filename,
        long sizeBytes,
        String status,
        String errorMessage,
        Integer pageCount,
        UUID uploadedBy,
        Instant createdAt) {

    static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getProjectId(),
                document.getFilename(),
                document.getSizeBytes(),
                document.getStatus().name(),
                document.getErrorMessage(),
                document.getPageCount(),
                document.getUploadedBy(),
                document.getCreatedAt());
    }
}
