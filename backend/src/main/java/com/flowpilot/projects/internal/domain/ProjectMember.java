package com.flowpilot.projects.internal.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.flowpilot.projects.ProjectRole;

/**
 * A user's membership in a single project, with a project role. The composite primary key
 * {@code (project_id, user_id)} means a user is a member of a project at most once.
 *
 * <p>{@code org_id} is denormalized so the composite FK {@code (project_id, org_id) -> project(id,
 * org_id)} can be enforced in SQL: a membership cannot point at a project from another organization.
 */
@Entity
@Table(name = "project_member")
@IdClass(ProjectMemberId.class)
public class ProjectMember {

    @Id
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProjectRole role;

    @Column(name = "weekly_capacity_points", nullable = false)
    private int weeklyCapacityPoints;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Version
    private Long version;

    protected ProjectMember() {
    }

    public ProjectMember(UUID projectId, UUID userId, UUID orgId, ProjectRole role,
                         int weeklyCapacityPoints, Instant joinedAt) {
        this.projectId = projectId;
        this.userId = userId;
        this.orgId = orgId;
        this.role = role;
        this.weeklyCapacityPoints = weeklyCapacityPoints;
        this.joinedAt = joinedAt;
    }

    public void changeRole(ProjectRole role) {
        this.role = role;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public ProjectRole getRole() {
        return role;
    }

    public int getWeeklyCapacityPoints() {
        return weeklyCapacityPoints;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Long getVersion() {
        return version;
    }
}
