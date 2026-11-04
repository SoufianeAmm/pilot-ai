package com.flowpilot.identity.internal.security;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import com.flowpilot.shared.security.AuthenticatedUser;

/** Spring Security authentication for a request carrying a valid access token. No authorities: permissions are checked per organization/project. */
class AccessTokenAuthentication extends AbstractAuthenticationToken {

    private final AuthenticatedUser user;

    AccessTokenAuthentication(AuthenticatedUser user) {
        super(List.of());
        this.user = user;
        setAuthenticated(true);
    }

    @Override
    public AuthenticatedUser getPrincipal() {
        return user;
    }

    @Override
    public Object getCredentials() {
        return null;
    }
}
