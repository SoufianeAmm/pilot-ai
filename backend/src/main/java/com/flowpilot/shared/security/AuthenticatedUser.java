package com.flowpilot.shared.security;

import java.util.UUID;

/**
 * The caller of an authenticated request, taken from a validated access token.
 *
 * <p>Inject it in any controller with {@code @AuthenticationPrincipal AuthenticatedUser user}.
 * It deliberately carries no roles: organization and project permissions are checked on every
 * request through OrganizationAccess / ProjectAccess, so revoked access takes effect immediately.
 */
public record AuthenticatedUser(UUID userId, String email) {
}
