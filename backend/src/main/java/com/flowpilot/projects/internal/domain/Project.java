package com.flowpilot.projects.internal.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.flowpilot.projects.ProjectSummary;

/**
 * A project owned by an organization. The {@code key} (e.g. "FP") plus {@code next_task_number}
 * produce human-readable task keys like "FP-42"; the counter is incremented under a row lock by the
 * tasks module. {@code (id, org_id)} is unique and is the target of every child composite FK.
 */
@Entity
@Table(name = "project")
public class Project {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    /** Per-org human prefix, e.g. "FP" in "FP-42". Unique within the organization. */
    @Column(name = "key", nullable = false, length = 10)
    private String key;

    @Column(nullable = false, length = 120)
    private String name;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProjectStatus status;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "next_task_number", nullable = false)
    private int nextTaskNumber;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Project() {
    }

    public Project(UUID id, UUID orgId, String key, String name, String description, ProjectStatus status,
                   LocalDate startDate, LocalDate targetDate, UUID ownerId, Instant createdAt) {
        this.id = id;
        this.orgId = orgId;
        this.key = key;
        this.name = name;
        this.description = description;
        this.status = status;
        this.startDate = startDate;
        this.targetDate = targetDate;
        this.ownerId = ownerId;
        this.nextTaskNumber = 1;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void update(String name, String description, ProjectStatus status,
                       LocalDate startDate, LocalDate targetDate, Instant now) {
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (status != null) {
            this.status = status;
        }
        if (startDate != null) {
            this.startDate = startDate;
        }
        if (targetDate != null) {
            this.targetDate = targetDate;
        }
        this.updatedAt = now;
    }

    /** @return the next human task number and increments the counter. Call while holding the row lock. */
    public int reserveNextTaskNumber() {
        return nextTaskNumber++;
    }

    public ProjectSummary toSummary() {
        return new ProjectSummary(id, orgId, key, name, status.name(), createdAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public int getNextTaskNumber() {
        return nextTaskNumber;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }

    /** Two projects are equal by id (the persistence id), ignoring the version. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Project that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
