package com.flowpilot.identity.internal.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A refresh token in a rotation family. Every login starts a new family; every refresh
 * revokes the current token and issues its replacement in the same family. A revoked token
 * that has a replacement and is presented again signals theft: the whole family is revoked.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false)
    private byte[] tokenHash;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "user_agent")
    private String userAgent;

    @Version
    private Long version;

    protected RefreshToken() {
    }

    public RefreshToken(UUID id, UUID userId, byte[] tokenHash, UUID familyId, Instant expiresAt,
                        Instant createdAt, String userAgent) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.userAgent = userAgent;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    /** True if this token was rotated (as opposed to revoked by logout). */
    public boolean isReplaced() {
        return replacedById != null;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public void replaceWith(UUID newTokenId, Instant now) {
        this.replacedById = newTokenId;
        this.revokedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
