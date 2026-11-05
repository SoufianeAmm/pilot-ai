package com.flowpilot.documents.internal.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.flowpilot.documents.internal.domain.Document;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByIdAndProjectIdAndDeletedAtIsNull(UUID id, UUID projectId);

    Page<Document> findByProjectIdAndDeletedAtIsNull(UUID projectId, Pageable pageable);

    boolean existsByProjectIdAndSha256AndDeletedAtIsNull(UUID projectId, byte[] sha256);
}
;