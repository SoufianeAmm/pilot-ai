package com.flowpilot.organizations;

import java.util.UUID;

/**
 * Events published by the organizations module (listen with @EventListener or
 * @TransactionalEventListener). The projects module must react to {@link MemberRemovedEvent}
 * by removing the user's project memberships; notifications reacts to {@link MemberJoinedEvent}.
 */
public final class OrganizationEvents {

    private OrganizationEvents() {
    }

    public record OrganizationCreatedEvent(UUID orgId, UUID ownerId) {
    }

    public record MemberJoinedEvent(UUID orgId, UUID userId, OrgRole role) {
    }

    public record MemberRoleChangedEvent(UUID orgId, UUID userId, OrgRole oldRole, OrgRole newRole, UUID changedBy) {
    }

    /** {@code removedBy} equals {@code userId} when the member left on their own. */
    public record MemberRemovedEvent(UUID orgId, UUID userId, UUID removedBy) {
    }
}
