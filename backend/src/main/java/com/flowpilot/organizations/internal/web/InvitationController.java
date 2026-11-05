package com.flowpilot.organizations.internal.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.flowpilot.organizations.OrgPermission;
import com.flowpilot.organizations.OrganizationAccess;
import com.flowpilot.organizations.internal.application.InvitationService;
import com.flowpilot.organizations.internal.web.OrganizationDtos.AcceptInvitationRequest;
import com.flowpilot.organizations.internal.web.OrganizationDtos.InvitationResponse;
import com.flowpilot.organizations.internal.web.OrganizationDtos.InviteRequest;
import com.flowpilot.organizations.internal.web.OrganizationDtos.OrganizationResponse;
import com.flowpilot.shared.security.AuthenticatedUser;

@RestController
class InvitationController {

    private final InvitationService invitations;
    private final OrganizationAccess access;

    InvitationController(InvitationService invitations, OrganizationAccess access) {
        this.invitations = invitations;
        this.access = access;
    }

    /** The response never contains the token: it is only sent to the invited email address. */
    @PostMapping("/api/v1/orgs/{orgId}/invitations")
    ResponseEntity<InvitationResponse> invite(@PathVariable UUID orgId, @Valid @RequestBody InviteRequest request,
                                              @AuthenticationPrincipal AuthenticatedUser user) {
        var scope = access.requirePermission(orgId, user.userId(), OrgPermission.MEMBER_INVITE);
        var invitation = InvitationResponse.from(invitations.invite(scope, request.email(), request.role()));
        return ResponseEntity.created(URI.create("/api/v1/orgs/" + orgId + "/invitations/" + invitation.id()))
                .body(invitation);
    }

    @GetMapping("/api/v1/orgs/{orgId}/invitations")
    List<InvitationResponse> listPending(@PathVariable UUID orgId, @AuthenticationPrincipal AuthenticatedUser user) {
        var scope = access.requirePermission(orgId, user.userId(), OrgPermission.MEMBER_INVITE);
        return invitations.listPending(scope).stream().map(InvitationResponse::from).toList();
    }

    @DeleteMapping("/api/v1/orgs/{orgId}/invitations/{invitationId}")
    ResponseEntity<Void> revoke(@PathVariable UUID orgId, @PathVariable UUID invitationId,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        invitations.revoke(access.requirePermission(orgId, user.userId(), OrgPermission.MEMBER_INVITE), invitationId);
        return ResponseEntity.noContent().build();
    }

    /** Any logged-in user may try; it only works with the account the invitation was sent to. Returns the joined organization. */
    @PostMapping("/api/v1/invitations/accept")
    OrganizationResponse accept(@Valid @RequestBody AcceptInvitationRequest request,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        return OrganizationResponse.from(invitations.accept(request.token(), user.userId()));
    }
}
