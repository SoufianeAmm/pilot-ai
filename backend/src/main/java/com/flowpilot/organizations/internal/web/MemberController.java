package com.flowpilot.organizations.internal.web;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flowpilot.organizations.OrgPermission;
import com.flowpilot.organizations.OrganizationAccess;
import com.flowpilot.organizations.internal.application.MembershipService;
import com.flowpilot.organizations.internal.web.OrganizationDtos.ChangeRoleRequest;
import com.flowpilot.organizations.internal.web.OrganizationDtos.MemberResponse;
import com.flowpilot.shared.security.AuthenticatedUser;
import com.flowpilot.shared.web.PageResponse;
import com.flowpilot.shared.web.Pageables;

@RestController
@RequestMapping("/api/v1/orgs/{orgId}/members")
class MemberController {

    private static final Set<String> SORTABLE = Set.of("joinedAt", "role");
    private static final Sort DEFAULT_SORT = Sort.by("joinedAt");

    private final MembershipService memberships;
    private final OrganizationAccess access;

    MemberController(MembershipService memberships, OrganizationAccess access) {
        this.memberships = memberships;
        this.access = access;
    }

    @GetMapping
    PageResponse<MemberResponse> list(@PathVariable UUID orgId, @PageableDefault(size = 20) Pageable pageable,
                                      @AuthenticationPrincipal AuthenticatedUser user) {
        var scope = access.requireMembership(orgId, user.userId());
        var page = memberships.listMembers(scope, Pageables.restrictSort(pageable, SORTABLE, DEFAULT_SORT));
        return PageResponse.of(page, MemberResponse::from);
    }

    @PatchMapping("/{userId}")
    MemberResponse changeRole(@PathVariable UUID orgId, @PathVariable UUID userId,
                              @Valid @RequestBody ChangeRoleRequest request,
                              @AuthenticationPrincipal AuthenticatedUser user) {
        var scope = access.requirePermission(orgId, user.userId(), OrgPermission.MEMBER_MANAGE);
        return MemberResponse.from(memberships.changeRole(scope, userId, request.role(), request.version()));
    }

    /** Removing yourself means leaving the organization and only needs membership. */
    @DeleteMapping("/{userId}")
    ResponseEntity<Void> remove(@PathVariable UUID orgId, @PathVariable UUID userId,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        memberships.removeMember(access.requireMembership(orgId, user.userId()), userId);
        return ResponseEntity.noContent().build();
    }
}
