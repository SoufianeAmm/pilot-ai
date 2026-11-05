package com.flowpilot.organizations.internal.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flowpilot.organizations.OrgPermission;
import com.flowpilot.organizations.OrganizationAccess;
import com.flowpilot.organizations.internal.application.OrganizationService;
import com.flowpilot.organizations.internal.web.OrganizationDtos.CreateOrganizationRequest;
import com.flowpilot.organizations.internal.web.OrganizationDtos.OrganizationResponse;
import com.flowpilot.organizations.internal.web.OrganizationDtos.UpdateOrganizationRequest;
import com.flowpilot.shared.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/v1/orgs")
class OrganizationController {

    private final OrganizationService organizations;
    private final OrganizationAccess access;

    OrganizationController(OrganizationService organizations, OrganizationAccess access) {
        this.organizations = organizations;
        this.access = access;
    }

    @PostMapping
    ResponseEntity<OrganizationResponse> create(@Valid @RequestBody CreateOrganizationRequest request,
                                                @AuthenticationPrincipal AuthenticatedUser user) {
        var created = OrganizationResponse.from(organizations.create(request.name(), request.slug(), user.userId()));
        return ResponseEntity.created(URI.create("/api/v1/orgs/" + created.id())).body(created);
    }

    /** The organizations the caller belongs to, with the caller's role in each. */
    @GetMapping
    List<OrganizationResponse> listMine(@AuthenticationPrincipal AuthenticatedUser user) {
        return organizations.listForUser(user.userId()).stream().map(OrganizationResponse::from).toList();
    }

    @GetMapping("/{orgId}")
    OrganizationResponse get(@PathVariable UUID orgId, @AuthenticationPrincipal AuthenticatedUser user) {
        return OrganizationResponse.from(organizations.get(access.requireMembership(orgId, user.userId())));
    }

    @PatchMapping("/{orgId}")
    OrganizationResponse update(@PathVariable UUID orgId, @Valid @RequestBody UpdateOrganizationRequest request,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        var scope = access.requirePermission(orgId, user.userId(), OrgPermission.ORG_UPDATE);
        return OrganizationResponse.from(
                organizations.update(scope, request.name(), request.timeZone(), request.version()));
    }
}
