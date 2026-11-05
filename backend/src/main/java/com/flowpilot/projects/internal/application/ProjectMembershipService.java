package com.flowpilot.projects.internal.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.flowpilot.audit.AuditActions;
import com.flowpilot.audit.AuditEvent;
import com.flowpilot.audit.AuditTrail;
import com.flowpilot.identity.IdentityApi;
import com.flowpilot.identity.UserSummary;
import com.flowpilot.organizations.MemberSummary;
import com.flowpilot.organizations.OrganizationDirectory;
import com.flowpilot.organizations.OrganizationEvents.MemberRemovedEvent;
import com.flowpilot.projects.ProjectEvents.ProjectMemberAddedEvent;
import com.flowpilot.projects.ProjectEvents.ProjectMemberRemovedEvent;
import com.flowpilot.projects.ProjectRole;
import com.flowpilot.projects.ProjectScope;
import com.flowpilot.projects.internal.domain.ProjectMember;
import com.flowpilot.projects.internal.persistence.ProjectMemberRepository;
import com.flowpilot.shared.id.Ids;
import com.flowpilot.shared.web.ApiException;

/**
 * Project membership add/list/change-role/remove. A new member must already belong to the
 * organization (checked through {@link OrganizationDirectory}). When a user is removed from the
 * organization, this service deletes all their project memberships in that org — see
 * {@link #onOrganizationMemberRemoved}.
 */
@Service
public class ProjectMembershipService {

    private final ProjectMemberRepository members;
    private final OrganizationDirectory directory;
    private final IdentityApi identity;
    private final AuditTrail audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ProjectMembershipService(ProjectMemberRepository members, OrganizationDirectory directory,
                                    IdentityApi identity, AuditTrail audit,
                                    ApplicationEventPublisher events, Clock clock) {
        this.members = members;
        this.directory = directory;
        this.identity = identity;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public ProjectMemberView add(ProjectScope scope, UUID userId, ProjectRole role, int weeklyCapacityPoints) {
        // The new member must already belong to the organization.
        MemberSummary orgMember = directory.findMember(scope.orgId(), userId)
                .orElseThrow(() -> ApiException.conflict("The user is not a member of the organization."));
        if (members.findByProjectIdAndUserId(scope.projectId(), userId).isPresent()) {
            throw alreadyMember();
        }
        ProjectMember member = new ProjectMember(scope.projectId(), orgMember.userId(), scope.orgId(), role,
                weeklyCapacityPoints, clock.instant());
        try {
            members.saveAndFlush(member);
        } catch (DataIntegrityViolationException ex) {
            // Only reachable when the same user is added twice at the same moment.
            throw alreadyMember();
        }
        audit.record(AuditEvent.success(AuditActions.PROJECT_MEMBER_ADDED, scope.orgId(), scope.userId())
                .withTarget("project_member", userId)
                .withDetail("role", role.name()));
        events.publishEvent(new ProjectMemberAddedEvent(scope.orgId(), scope.projectId(), userId, role));
        return viewOf(member);
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberView> list(ProjectScope scope) {
        List<ProjectMember> rows = members.findAllByProjectId(scope.projectId());
        Map<UUID, UserSummary> users = identity.findUsers(
                rows.stream().map(ProjectMember::getUserId).collect(Collectors.toSet()));
        return rows.stream()
                .filter(m -> users.containsKey(m.getUserId()))
                .map(m -> new ProjectMemberView(m, users.get(m.getUserId())))
                .toList();
    }

    @Transactional
    public ProjectMemberView changeRole(ProjectScope scope, UUID userId, ProjectRole role, long expectedVersion) {
        ProjectMember member = load(scope, userId);
        if (!Objects.equals(member.getVersion(), expectedVersion)) {
            throw ApiException.conflict("The membership was changed by someone else. Reload and try again.");
        }
        member.changeRole(role);
        members.flush();
        audit.record(AuditEvent.success(AuditActions.PROJECT_MEMBER_ROLE_CHANGED, scope.orgId(), scope.userId())
                .withTarget("project_member", userId)
                .withDetail("role", role.name()));
        return viewOf(member);
    }

    @Transactional
    public void remove(ProjectScope scope, UUID userId) {
        ProjectMember member = load(scope, userId);
        members.delete(member);
        audit.record(AuditEvent.success(AuditActions.PROJECT_MEMBER_REMOVED, scope.orgId(), scope.userId())
                .withTarget("project_member", userId));
        events.publishEvent(new ProjectMemberRemovedEvent(scope.orgId(), scope.projectId(), userId, scope.userId()));
    }

    /**
     * When a user is removed from an organization, delete all their project memberships in that org.
     * Runs AFTER COMMIT (a rolled-back org removal leaves project access intact) in a NEW transaction,
     * because the original transaction has already committed (Spring Framework 7 requires
     * {@link Propagation#REQUIRES_NEW} or {@link Propagation#NOT_SUPPORTED} here).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrganizationMemberRemoved(MemberRemovedEvent event) {
        members.deleteByOrgIdAndUserId(event.orgId(), event.userId());
    }

    private static ApiException alreadyMember() {
        return ApiException.conflict("The user is already a member of this project.");
    }

    private ProjectMember load(ProjectScope scope, UUID userId) {
        return members.findByProjectIdAndUserId(scope.projectId(), userId)
                .orElseThrow(() -> ApiException.notFound("Project member not found."));
    }

    /** Pair a membership row with the member's identity (display name, email) for the response. */
    private ProjectMemberView viewOf(ProjectMember member) {
        UserSummary user = identity.findUser(member.getUserId())
                .orElseThrow(() -> ApiException.conflict("The member's user account could not be found."));
        return new ProjectMemberView(member, user);
    }
}