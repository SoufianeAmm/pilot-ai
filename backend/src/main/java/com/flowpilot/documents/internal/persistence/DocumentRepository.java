package com.flowpilot.documents.internal.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.flowpilot.documents.internal.domain.Document;

/** Every query carries both the organization and the project (tenant rule: always org AND project). */
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByIdAndOrgIdAndProjectIdAndDeletedAtIsNull(UUID id, UUID orgId, UUID projectId);

    Page<Document> findByOrgIdAndProjectIdAndDeletedAtIsNull(UUID orgId, UUID projectId, Pageable pageable);

    boolean existsByOrgIdAndProjectIdAndSha256AndDeletedAtIsNull(UUID orgId, UUID projectId, byte[] sha256);
}
