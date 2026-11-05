package com.flowpilot.organizations.internal.application;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowpilot.audit.AuditActions;
import com.flowpilot.audit.AuditEvent;
import com.flowpilot.audit.AuditTrail;
import com.flowpilot.identity.IdentityApi;
import com.flowpilot.identity.UserSummary;
import com.flowpilot.organizations.OrgPermission;
import com.flowpilot.organizations.OrgRole;
import com.flowpilot.organizations.OrgScope;
import com.flowpilot.organizations.OrganizationEvents.MemberRemovedEvent;
import com.flowpilot.organizations.OrganizationEvents.MemberRoleChangedEvent;
import com.flowpilot.organizations.internal.domain.Membership;
import com.flowpilot.organizations.internal.persistence.MembershipRepository;
import com.flowpilot.organizations.internal.persistence.OrganizationRepository;
import com.flowpilot.shared.web.ApiException;

@Service
public class MembershipService {

    private final MembershipRepository memberships;
    private final OrganizationRepository organizations;
    private final RolePolicy rolePolicy;
    private final IdentityApi identity;
    private final AuditTrail audit;
    private final ApplicationEventPublisher events;

    public MembershipService(MembershipRepository memberships, OrganizationRepository organizations,
                             RolePolicy rolePolicy, IdentityApi identity, AuditTrail audit,
                             ApplicationEventPublisher events) {
        this.memberships = memberships;
        this.organizations = organizations;
        this.rolePolicy = rolePolicy;
        this.identity = identity;
        this.audit = audit;
        this.events = events;
    }

    /** One query for the page of memberships and one for the users: no N+1. */
    @Transactional(readOnly = true)
    public Page<MemberView> listMembers(OrgScope scope, Pageable pageable) {
        Page<Membership> page = memberships.findAllByOrgId(scope.orgId(), pageable);
        Map<UUID, UserSummary> users = identity.findUsers(
                page.getContent().stream().map(Membership::getUserId).collect(Collectors.toSet()));
        return page.map(m -> toView(m, users.get(m.getUserId())));
    }

    /** Caller must hold MEMBER_MANAGE. */
    @Transactional
    public MemberView changeRole(OrgScope actor, UUID targetUserId, OrgRole newRole, long expectedVersion) {
        lockOrganization(actor.orgId());
        Membership target = loadMember(actor.orgId(), targetUserId);
        if (!Objects.equals(target.getVersion(), expectedVersion)) {
            throw ApiException.conflict("This member was changed by someone else. Reload and try again.");
        }
        OrgRole oldRole = target.getRole();
        if (oldRole == newRole) {
            return toView(target);
        }
        if (!rolePolicy.canAssign(actor.role(), oldRole, newRole)) {
            audit.record(AuditEvent.denied(AuditActions.MEMBER_ROLE_CHANGED, actor.orgId(), actor.userId())
                    .withTarget("user", targetUserId)
                    .withDetail("from", oldRole.name()).withDetail("to", newRole.name()));
            throw ApiException.forbidden("You cannot change this member to that role.");
        }
        if (oldRole == OrgRole.OWNER) {
            rolePolicy.ensureOwnerRemains(actor.orgId());
        }
        target.changeRole(newRole);
        memberships.flush();
        events.publishEvent(new MemberRoleChangedEvent(actor.orgId(), targetUserId, oldRole, newRole, actor.userId()));
        audit.record(AuditEvent.success(AuditActions.MEMBER_ROLE_CHANGED, actor.orgId(), actor.userId())
                .withTarget("user", targetUserId)
                .withDetail("from", oldRole.name()).withDetail("to", newRole.name()));
        return toView(target);
    }

    /**
     * Removes a member. Any member may remove themselves (leave); removing someone else needs
     * MEMBER_MANAGE and must respect the role rules.
     */
    @Transactional
    public void removeMember(OrgScope actor, UUID targetUserId) {
        lockOrganization(actor.orgId());
        Membership target = loadMember(actor.orgId(), targetUserId);
        boolean leaving = targetUserId.equals(actor.userId());
        if (!leaving && (!actor.has(OrgPermission.MEMBER_MANAGE) || !rolePolicy.canRemove(actor.role(), target.getRole()))) {
            audit.record(AuditEvent.denied(AuditActions.MEMBER_REMOVED, actor.orgId(), actor.userId())
                    .withTarget("user", targetUserId));
            throw ApiException.forbidden("You cannot remove this member.");
        }
        if (target.getRole() == OrgRole.OWNER) {
            rolePolicy.ensureOwnerRemains(actor.orgId());
        }
        memberships.delete(target);
        events.publishEvent(new MemberRemovedEvent(actor.orgId(), targetUserId, actor.userId()));
        audit.record(AuditEvent.success(AuditActions.MEMBER_REMOVED, actor.orgId(), actor.userId())
                .withTarget("user", targetUserId)
                .withDetail("left", leaving));
    }

    private void lockOrganization(UUID orgId) {
        organizations.findByIdForUpdate(orgId).orElseThrow(() -> ApiException.notFound("Organization not found."));
    }

    private Membership loadMember(UUID orgId, UUID userId) {
        return memberships.findByOrgIdAndUserId(orgId, userId)
                .orElseThrow(() -> ApiException.notFound("Member not found."));
    }

    private MemberView toView(Membership membership) {
        return toView(membership, identity.findUser(membership.getUserId())
                .orElseThrow(() -> ApiException.notFound("Member not found.")));
    }

    private static MemberView toView(Membership membership, UserSummary user) {
        return new MemberView(OrganizationAccessService.toSummary(membership, user), membership.getVersion());
    }
}
