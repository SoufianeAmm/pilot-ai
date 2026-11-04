package com.flowpilot.identity.internal.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "app_user")
public class User {

    @Id
    private UUID id;

    /** Always stored normalized (see {@link #normalizeEmail}); the DB enforces uniqueness. */
    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected User() {
    }

    public User(UUID id, String normalizedEmail, String passwordHash, String displayName, Instant createdAt) {
        this.id = id;
        this.email = normalizedEmail;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.status = UserStatus.ACTIVE;
        this.createdAt = createdAt;
    }

    /** Emails are compared case-insensitively: trim and lower-case before storing or looking up. */
    public static String normalizeEmail(String raw) {
        return raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
    }

    public void changeDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void disable() {
        this.status = UserStatus.DISABLED;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
