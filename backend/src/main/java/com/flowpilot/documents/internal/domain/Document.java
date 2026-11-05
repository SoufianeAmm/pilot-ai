package com.flowpilot.documents.internal.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "document")
public class Document {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** SHA-256 of the file content (32 bytes), used to detect duplicates. */
    @Column(nullable = false)
    private byte[] sha256;

    /** Where the file lives in DocumentStorage. Never derived from the user-supplied filename. */
    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DocumentStatus status;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    protected Document() {
    }

    public Document(UUID id, UUID orgId, UUID projectId, String filename, String contentType,
                    long sizeBytes, byte[] sha256, String storageKey, UUID uploadedBy, Instant now) {
        this.id = id;
        this.orgId = orgId;
        this.projectId = projectId;
        this.filename = filename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storageKey = storageKey;
        this.uploadedBy = uploadedBy;
        this.status = DocumentStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markProcessing(Instant now) {
        this.status = DocumentStatus.PROCESSING;
        this.errorMessage = null;
        this.updatedAt = now;
    }

    public void markReady(int pageCount, Instant now) {
        this.status = DocumentStatus.READY;
        this.pageCount = pageCount;
        this.errorMessage = null;
        this.updatedAt = now;
    }

    public void markFailed(String message, Instant now) {
        this.status = DocumentStatus.FAILED;
        this.errorMessage = message == null ? null : message.substring(0, Math.min(message.length(), 500));
        this.updatedAt = now;
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public UUID getId() { return id; }
    public UUID getOrgId() { return orgId; }
    public UUID getProjectId() { return projectId; }
    public String getFilename() { return filename; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public byte[] getSha256() { return sha256; }
    public String getStorageKey() { return storageKey; }
    public DocumentStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public Integer getPageCount() { return pageCount; }
    public UUID getUploadedBy() { return uploadedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
}