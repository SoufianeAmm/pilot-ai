package com.flowpilot.identity.internal.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.flowpilot.identity.internal.application.AuthExceptions.InvalidAccessTokenException;
import com.flowpilot.identity.internal.application.JwtService;

/**
 * Authenticates requests that carry "Authorization: Bearer &lt;access token&gt;". A missing or invalid
 * token leaves the request anonymous; protected endpoints then answer 401 through the entry point.
 * Created by SecurityConfig (not a @Component), so it only runs inside the security filter chain.
 */
class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwt;

    JwtAuthenticationFilter(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            try {
                var user = jwt.parseAndValidate(token);
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new AccessTokenAuthentication(user));
                SecurityContextHolder.setContext(context);
            } catch (InvalidAccessTokenException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
