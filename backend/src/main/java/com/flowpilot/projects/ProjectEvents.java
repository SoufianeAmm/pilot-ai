package com.flowpilot.projects;

import java.util.UUID;

/**
 * Events published by the projects module, listened to with {@code @EventListener} or
 * {@code @TransactionalEventListener}. They are published within the transaction and stored by the
 * Spring Modulith event-publication registry (outbox), so a crash after commit does not lose them.
 *
 * <p>Listeners: {@code notifications} reacts to member/milestone events; {@code collaboration} turns
 * them into activity-feed entries; {@code analytics} may snapshot on milestone completion.
 */
public final class ProjectEvents {

    private ProjectEvents() {
    }

    public record ProjectCreatedEvent(UUID orgId, UUID projectId, UUID ownerId) {
    }

    public record ProjectArchivedEvent(UUID orgId, UUID projectId, UUID archivedBy) {
    }

    public record ProjectMemberAddedEvent(UUID orgId, UUID projectId, UUID userId, ProjectRole role) {
    }

    /** {@code removedBy} equals {@code userId} when the member left on their own. */
    public record ProjectMemberRemovedEvent(UUID orgId, UUID projectId, UUID userId, UUID removedBy) {
    }

    public record MilestoneCompletedEvent(UUID orgId, UUID projectId, UUID milestoneId, int number) {
    }
}
