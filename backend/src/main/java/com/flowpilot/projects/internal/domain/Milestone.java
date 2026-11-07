package com.flowpilot.projects.internal.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A milestone within a project, numbered "M-n" (unique per project). A milestone is {@code OPEN}
 * until it is completed; completion publishes a {@code MilestoneCompletedEvent} (for notifications
 * and analytics).
 */
@Entity
@Table(name = "milestone")
public class Milestone {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private int number;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MilestoneStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected Milestone() {
    }

    public Milestone(UUID id, UUID orgId, UUID projectId, int number, String name,
                     LocalDate dueDate, Instant createdAt) {
        this.id = id;
        this.orgId = orgId;
        this.projectId = projectId;
        this.number = number;
        this.name = name;
        this.dueDate = dueDate;
        this.status = MilestoneStatus.OPEN;
        this.createdAt = createdAt;
    }

    public void complete() {
        this.status = MilestoneStatus.COMPLETED;
    }

    public void reopen() {
        this.status = MilestoneStatus.OPEN;
    }

    /** Null arguments leave the field unchanged. */
    public void update(String name, LocalDate dueDate) {
        if (name != null) {
            this.name = name;
        }
        if (dueDate != null) {
            this.dueDate = dueDate;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public int getNumber() {
        return number;
    }

    public String getName() {
        return name;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public MilestoneStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }
}
