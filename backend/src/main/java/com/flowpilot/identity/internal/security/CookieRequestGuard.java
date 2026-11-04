package com.flowpilot.identity.internal.security;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.flowpilot.shared.web.ApiException;

/**
 * CSRF protection for the two endpoints authenticated by the refresh cookie instead of a bearer
 * token. Besides SameSite=Strict, a request must (1) carry the custom X-Requested-With header,
 * which a cross-site form cannot send and cross-site scripts can only send after a CORS preflight
 * that we reject, and (2) if the browser sends an Origin header, come from an allowed origin.
 */
class CookieRequestGuard extends OncePerRequestFilter {

    static final String REQUIRED_HEADER = "X-Requested-With";
    private static final Set<String> GUARDED_PATHS = Set.of("/api/v1/auth/refresh", "/api/v1/auth/logout");

    private final List<String> allowedOrigins;
    private final HandlerExceptionResolver exceptionResolver;

    CookieRequestGuard(List<String> allowedOrigins, HandlerExceptionResolver exceptionResolver) {
        this.allowedOrigins = allowedOrigins;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !GUARDED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        String marker = request.getHeader(REQUIRED_HEADER);
        if (marker == null || marker.isBlank() || (origin != null && !allowedOrigins.contains(origin))) {
            exceptionResolver.resolveException(request, response, null,
                    ApiException.forbidden("This request must come from the FlowPilot web application."));
            return;
        }
        chain.doFilter(request, response);
    }
}
