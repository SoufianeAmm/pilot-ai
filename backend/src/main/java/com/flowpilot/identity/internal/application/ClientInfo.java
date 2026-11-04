package com.flowpilot.identity.internal.application;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;

/**
 * Who is calling, for rate limiting and audit. The IP is the one Tomcat resolved: behind Nginx,
 * {@code server.forward-headers-strategy=native} takes it from X-Forwarded-For (trusted proxies only).
 */
public record ClientInfo(String ip, String userAgent) {

    private static final int MAX_USER_AGENT = 255;

    public static ClientInfo from(HttpServletRequest request) {
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        if (userAgent != null && userAgent.length() > MAX_USER_AGENT) {
            userAgent = userAgent.substring(0, MAX_USER_AGENT);
        }
        return new ClientInfo(request.getRemoteAddr(), userAgent);
    }
}
