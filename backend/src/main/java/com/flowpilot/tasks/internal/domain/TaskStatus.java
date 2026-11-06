package com.flowpilot.tasks.internal.domain;

/** Task lifecycle. {@code DONE} and {@code CANCELLED} are terminal (a completed_at is recorded). */
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    IN_REVIEW,
    DONE,
    CANCELLED;

    public boolean isTerminal() {
        return this == DONE || this == CANCELLED;
    }
}
