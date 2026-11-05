package com.flowpilot.projects.internal.domain;

/** Lifecycle of a project. Stored as text with a CHECK constraint (see V25__projects.sql). */
public enum ProjectStatus {
    PLANNED,
    ACTIVE,
    ON_HOLD,
    COMPLETED,
    ARCHIVED
}
