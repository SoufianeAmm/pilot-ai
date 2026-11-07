package com.flowpilot.organizations.internal.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "organization")
public class Organization {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 60)
    private String slug;

    /** Used to decide what "today" and "overdue" mean for this organization. */
    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected Organization() {
    }

    public Organization(UUID id, String name, String slug, ZoneId timeZone, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.timeZone = timeZone.getId();
        this.createdAt = createdAt;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changeTimeZone(ZoneId zone) {
        this.timeZone = zone.getId();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public ZoneId getTimeZone() {
        return ZoneId.of(timeZone);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getVersion() {
        return version;
    }
}
