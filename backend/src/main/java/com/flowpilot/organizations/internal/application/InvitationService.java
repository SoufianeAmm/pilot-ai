package com.flowpilot.organizations.internal.application;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowpilot.audit.AuditActions;
import com.flowpilot.audit.AuditEvent;
import com.flowpilot.audit.AuditTrail;
import com.flowpilot.identity.IdentityApi;
import com.flowpilot.identity.UserSummary;
import com.flowpilot.organizations.OrgRole;
import com.flowpilot.organizations.OrgScope;
import com.flowpilot.organizations.OrganizationEvents.MemberJoinedEvent;
import com.flowpilot.organizations.internal.domain.Invitation;
import com.flowpilot.organizations.internal.domain.Membership;
import com.flowpilot.organizations.internal.domain.Organization;
import com.flowpilot.organizations.internal.persistence.InvitationRepository;
import com.flowpilot.organizations.internal.persistence.MembershipRepository;
import com.flowpilot.organizations.internal.persistence.OrganizationRepository;
import com.flowpilot.shared.id.Ids;
import com.flowpilot.shared.web.ApiException;

/**
 * Invitations: a 256-bit random token is sent in the link; only its SHA-256 hash is stored. A token
 * is single-use, expires, and can only be accepted by an account with the invited email address.
 */
@Service
public class InvitationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final InvitationRepository invitations;
    private final MembershipRepository memberships;
    private final OrganizationRepository organizations;
    private final RolePolicy rolePolicy;
    private final IdentityApi identity;
    private final InvitationMailer mailer;
    private final AuditTrail audit;
    private final ApplicationEventPublisher events;
    private final OrganizationProperties properties;
    private final Clock clock;

    public InvitationService(InvitationRepository invitations, MembershipRepository memberships,
                             OrganizationRepository organizations, RolePolicy rolePolicy, IdentityApi identity,
                             InvitationMailer mailer, AuditTrail audit, ApplicationEventPublisher events,
                             OrganizationProperties properties, Clock clock) {
        this.invitations = invitations;
        this.memberships = memberships;
        this.organizations = organizations;
        this.rolePolicy = rolePolicy;
        this.identity = identity;
        this.mailer = mailer;
        this.audit = audit;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    /** Caller must hold MEMBER_INVITE. Re-inviting the same email replaces the previous open invitation. */
    @Transactional
    public Invitation invite(OrgScope actor, String rawEmail, OrgRole role) {
        String email = normalizeEmail(rawEmail);
        if (!rolePolicy.canAssign(actor.role(), null, role)) {
            audit.record(AuditEvent.denied(AuditActions.MEMBER_INVITED, actor.orgId(), actor.userId())
                    .withDetail("email", email).withDetail("role", role.name()));
            throw ApiException.forbidden("You cannot invite someone with a role higher than your own.");
        }
        boolean alreadyMember = identity.findByEmail(email)
                .map(user -> memberships.existsByOrgIdAndUserId(actor.orgId(), user.id()))
                .orElse(false);
        if (alreadyMember) {
            throw ApiException.conflict("This person is already a member of the organization.");
        }
        Instant now = clock.instant();
        invitations.findOpen(actor.orgId(), email).ifPresent(previous -> {
            previous.revoke(now);
            invitations.flush(); // free the "one open invitation" slot before inserting the new one
        });

        String rawToken = newToken();
        Invitation invitation = new Invitation(Ids.newId(), actor.orgId(), email, role, hash(rawToken), actor.userId(),
                now, now.plus(properties.invitationTtl()));
        invitations.saveAndFlush(invitation);
        Organization organization = organizations.findById(actor.orgId())
                .orElseThrow(() -> ApiException.notFound("Organization not found."));
        mailer.send(email, organization.getName(), acceptLink(rawToken));
        audit.record(AuditEvent.success(AuditActions.MEMBER_INVITED, actor.orgId(), actor.userId())
                .withTarget("invitation", invitation.getId())
                .withDetail("email", email).withDetail("role", role.name()));
        return invitation;
    }

    @Transactional
    public OrganizationWithRole accept(String rawToken, UUID userId) {
        Instant now = clock.instant();
        Invitation invitation = invitations.findByTokenHashForUpdate(hash(rawToken))
                .filter(candidate -> candidate.isPending(now))
                .orElseThrow(() -> ApiException.notFound("This invitation does not exist or is no longer valid."));
        UserSummary user = identity.findUser(userId)
                .orElseThrow(() -> ApiException.notFound("User not found."));
        if (!user.email().equals(invitation.getEmail())) {
            audit.record(AuditEvent.denied(AuditActions.MEMBER_JOINED, invitation.getOrgId(), userId)
                    .withTarget("invitation", invitation.getId()));
            throw ApiException.forbidden("This invitation was sent to a different email address.");
        }
        if (memberships.existsByOrgIdAndUserId(invitation.getOrgId(), userId)) {
            throw ApiException.conflict("You are already a member of this organization.");
        }
        try {
            memberships.saveAndFlush(new Membership(Ids.newId(), invitation.getOrgId(), userId, invitation.getRole(), now));
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("You are already a member of this organization.");
        }
        invitation.accept(now);
        events.publishEvent(new MemberJoinedEvent(invitation.getOrgId(), userId, invitation.getRole()));
        audit.record(AuditEvent.success(AuditActions.MEMBER_JOINED, invitation.getOrgId(), userId)
                .withTarget("invitation", invitation.getId())
                .withDetail("role", invitation.getRole().name()));
        Organization organization = organizations.findById(invitation.getOrgId())
                .orElseThrow(() -> ApiException.notFound("Organization not found."));
        return new OrganizationWithRole(organization, invitation.getRole());
    }

    /** Caller must hold MEMBER_INVITE. */
    @Transactional
    public void revoke(OrgScope actor, UUID invitationId) {
        Invitation invitation = invitations.findByIdAndOrgId(invitationId, actor.orgId())
                .filter(Invitation::isOpen)
                .orElseThrow(() -> ApiException.notFound("Invitation not found."));
        invitation.revoke(clock.instant());
        audit.record(AuditEvent.success(AuditActions.INVITATION_REVOKED, actor.orgId(), actor.userId())
                .withTarget("invitation", invitationId));
    }

    /** Caller must hold MEMBER_INVITE. */
    @Transactional(readOnly = true)
    public List<Invitation> listPending(OrgScope actor) {
        return invitations.findPending(actor.orgId(), clock.instant());
    }

    private URI acceptLink(String rawToken) {
        return URI.create(properties.acceptUrl() + "?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8));
    }

    static String normalizeEmail(String raw) {
        return raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
    }

    private static String newToken() {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    static byte[] hash(String rawToken) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
