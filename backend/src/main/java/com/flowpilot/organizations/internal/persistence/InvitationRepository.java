package com.flowpilot.organizations.internal.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.flowpilot.organizations.internal.domain.Invitation;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    /** Row lock, so a token cannot be accepted twice by two concurrent requests. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invitation i where i.tokenHash = :hash")
    Optional<Invitation> findByTokenHashForUpdate(@Param("hash") byte[] tokenHash);

    /** Always scoped by organization: an invitation id from another organization is not found. */
    Optional<Invitation> findByIdAndOrgId(UUID id, UUID orgId);

    @Query("select i from Invitation i where i.orgId = :orgId and i.email = :email "
            + "and i.acceptedAt is null and i.revokedAt is null")
    Optional<Invitation> findOpen(@Param("orgId") UUID orgId, @Param("email") String email);

    @Query("select i from Invitation i where i.orgId = :orgId and i.acceptedAt is null and i.revokedAt is null "
            + "and i.expiresAt > :now order by i.createdAt desc")
    List<Invitation> findPending(@Param("orgId") UUID orgId, @Param("now") Instant now);
}
