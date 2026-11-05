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

@Entity
@Table(name = "organization_membership")
public class Membership {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrgRole role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Version
    private Long version;

    protected Membership() {
    }

    public Membership(UUID id, UUID orgId, UUID userId, OrgRole role, Instant joinedAt) {
        this.id = id;
        this.orgId = orgId;
        this.userId = userId;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public void changeRole(OrgRole role) {
        this.role = role;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getUserId() {
        return userId;
    }

    public OrgRole getRole() {
        return role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Long getVersion() {
        return version;
    }
}
