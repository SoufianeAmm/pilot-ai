package com.flowpilot.tasks;

import java.util.UUID;

/**
 * Events published by the tasks module, listened to with {@code @EventListener} or
 * {@code @TransactionalEventListener}. Published within the transaction and stored by the Spring
 * Modulith event-publication registry (outbox), so a crash after commit does not lose them.
 *
 * <p>Listeners: {@code collaboration} turns them into activity-feed entries; {@code notifications}
 * reacts to assignments; {@code analytics} may snapshot on status changes.
 */
public final class TaskEvents {

    private TaskEvents() {
    }

    public record TaskCreatedEvent(UUID orgId, UUID projectId, UUID taskId, int number, UUID reporterId) {
    }

    public record TaskStatusChangedEvent(UUID orgId, UUID projectId, UUID taskId,
                                          String oldStatus, String newStatus, UUID changedBy) {
    }

    public record TaskDeletedEvent(UUID orgId, UUID projectId, UUID taskId, UUID deletedBy) {
    }

    public record TaskDependencyAddedEvent(UUID orgId, UUID projectId, UUID taskId, UUID dependsOnTaskId) {
    }
}
