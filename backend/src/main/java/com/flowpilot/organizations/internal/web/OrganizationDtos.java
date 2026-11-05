package com.flowpilot.organizations.internal.web;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.flowpilot.organizations.OrgRole;
import com.flowpilot.organizations.internal.application.MemberView;
import com.flowpilot.organizations.internal.application.OrganizationWithRole;
import com.flowpilot.organizations.internal.domain.Invitation;

/** Request and response bodies of the organization, member and invitation endpoints. */
final class OrganizationDtos {

    private OrganizationDtos() {
    }

    /** @param slug optional; derived from the name when omitted */
    record CreateOrganizationRequest(@NotBlank @Size(max = 100) String name,
                                     @Size(min = 2, max = 60) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                                             message = "may only contain lower-case letters, digits and single hyphens")
                                     String slug) {
    }

    /** Null fields are left unchanged; version must match the current one (optimistic locking). */
    record UpdateOrganizationRequest(@Size(min = 1, max = 100) String name,
                                     @Size(min = 1, max = 64) String timeZone,
                                     @NotNull Long version) {
    }

    record OrganizationResponse(UUID id, String name, String slug, String timeZone, OrgRole role,
                                Instant createdAt, long version) {

        static OrganizationResponse from(OrganizationWithRole view) {
            var org = view.organization();
            return new OrganizationResponse(org.getId(), org.getName(), org.getSlug(), org.getTimeZone().getId(),
                    view.role(), org.getCreatedAt(), org.getVersion());
        }
    }

    record MemberResponse(UUID userId, String displayName, String email, OrgRole role, Instant joinedAt,
                          long version) {

        static MemberResponse from(MemberView view) {
            var member = view.member();
            return new MemberResponse(member.userId(), member.displayName(), member.email(), member.role(),
                    member.joinedAt(), view.version());
        }
    }

    record ChangeRoleRequest(@NotNull OrgRole role, @NotNull Long version) {
    }

    record InviteRequest(@NotBlank @Email @Size(max = 254) String email, @NotNull OrgRole role) {
    }

    record InvitationResponse(UUID id, String email, OrgRole role, UUID invitedBy, Instant createdAt,
                              Instant expiresAt) {

        static InvitationResponse from(Invitation invitation) {
            return new InvitationResponse(invitation.getId(), invitation.getEmail(), invitation.getRole(),
                    invitation.getInvitedBy(), invitation.getCreatedAt(), invitation.getExpiresAt());
        }
    }

    record AcceptInvitationRequest(@NotBlank @Size(max = 100) String token) {

        @Override
        public String toString() {
            return "AcceptInvitationRequest[token=***]";
        }
    }
}
