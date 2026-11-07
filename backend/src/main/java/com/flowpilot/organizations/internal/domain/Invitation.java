package com.flowpilot.organizations.internal.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.flowpilot.organizations.OrgRole;

/** A single-use, expiring invitation to join an organization with a given role. */
@Entity
@Table(name = "invitation")
public class Invitation {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    /** Normalized (trimmed, lower-cased), like app_user.email. */
    @Column(nullable = false, length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrgRole role;

    @Column(name = "token_hash", nullable = false)
    private byte[] tokenHash;

    @Column(name = "invited_by", nullable = false)
    private UUID invitedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Version
    private Long version;

    protected Invitation() {
    }

    public Invitation(UUID id, UUID orgId, String normalizedEmail, OrgRole role, byte[] tokenHash, UUID invitedBy,
                      Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.orgId = orgId;
        this.email = normalizedEmail;
        this.role = role;
        this.tokenHash = tokenHash;
        this.invitedBy = invitedBy;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /** Neither accepted nor revoked, and not expired. */
    public boolean isPending(Instant now) {
        return acceptedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    /** Neither accepted nor revoked (possibly expired): still occupies the "one open invitation" slot. */
    public boolean isOpen() {
        return acceptedAt == null && revokedAt == null;
    }

    public void accept(Instant now) {
        this.acceptedAt = now;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public String getEmail() {
        return email;
    }

    public OrgRole getRole() {
        return role;
    }

    public UUID getInvitedBy() {
        return invitedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
