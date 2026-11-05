package com.flowpilot.organizations.internal.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowpilot.audit.AuditActions;
import com.flowpilot.audit.AuditEvent;
import com.flowpilot.audit.AuditTrail;
import com.flowpilot.identity.IdentityApi;
import com.flowpilot.identity.UserSummary;
import com.flowpilot.organizations.MemberSummary;
import com.flowpilot.organizations.OrgPermission;
import com.flowpilot.organizations.OrgRole;
import com.flowpilot.organizations.OrgScope;
import com.flowpilot.organizations.OrganizationAccess;
import com.flowpilot.organizations.OrganizationDirectory;
import com.flowpilot.organizations.internal.domain.Membership;
import com.flowpilot.organizations.internal.persistence.MembershipRepository;
import com.flowpilot.shared.web.ApiException;

@Service
@Transactional(readOnly = true)
class OrganizationAccessService implements OrganizationAccess, OrganizationDirectory {

    private final MembershipRepository memberships;
    private final IdentityApi identity;
    private final AuditTrail audit;

    OrganizationAccessService(MembershipRepository memberships, IdentityApi identity, AuditTrail audit) {
        this.memberships = memberships;
        this.identity = identity;
        this.audit = audit;
    }

    @Override
    public OrgScope requireMembership(UUID orgId, UUID userId) {
        if (orgId == null || userId == null) {
            throw notFound();
        }
        return memberships.findByOrgIdAndUserId(orgId, userId)
                .map(m -> new OrgScope(m.getOrgId(), m.getUserId(), m.getRole()))
                .orElseThrow(OrganizationAccessService::notFound);
    }

    @Override
    public OrgScope requirePermission(UUID orgId, UUID userId, OrgPermission permission) {
        OrgScope scope = requireMembership(orgId, userId);
        if (!scope.has(permission)) {
            audit.record(AuditEvent.denied(AuditActions.ACCESS_DENIED, orgId, userId)
                    .withDetail("permission", permission.name())
                    .withDetail("role", scope.role().name()));
            scope.require(permission);
        }
        return scope;
    }

    @Override
    public Optional<OrgRole> findRole(UUID orgId, UUID userId) {
        return memberships.findByOrgIdAndUserId(orgId, userId).map(Membership::getRole);
    }

    @Override
    public Optional<MemberSummary> findMember(UUID orgId, UUID userId) {
        return memberships.findByOrgIdAndUserId(orgId, userId)
                .flatMap(m -> identity.findUser(userId).map(user -> toSummary(m, user)));
    }

    @Override
    public List<MemberSummary> listMembers(UUID orgId) {
        List<Membership> members = memberships.findAllByOrgId(orgId);
        Map<UUID, UserSummary> users = identity.findUsers(
                members.stream().map(Membership::getUserId).collect(Collectors.toSet()));
        return members.stream()
                .filter(m -> users.containsKey(m.getUserId()))
                .map(m -> toSummary(m, users.get(m.getUserId())))
                .toList();
    }

    static MemberSummary toSummary(Membership membership, UserSummary user) {
        return new MemberSummary(membership.getUserId(), user.displayName(), user.email(), membership.getRole(),
                membership.getJoinedAt());
    }

    /** Same answer for "does not exist" and "exists but you are not a member". */
    private static ApiException notFound() {
        return ApiException.notFound("Organization not found.");
    }
}
