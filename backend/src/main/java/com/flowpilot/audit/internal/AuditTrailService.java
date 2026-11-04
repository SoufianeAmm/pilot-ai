package com.flowpilot.audit.internal;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.flowpilot.audit.AuditEvent;
import com.flowpilot.audit.AuditTrail;
import com.flowpilot.shared.web.RequestIdFilter;

import tools.jackson.databind.json.JsonMapper;

/**
 * Writes audit entries with plain SQL (the log is append-only, so no entity is needed for writes).
 * Request context (client IP, user agent, request id) is added automatically when available.
 */
@Service
class AuditTrailService implements AuditTrail {

    private static final int MAX_USER_AGENT = 255;

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Clock clock;

    AuditTrailService(JdbcClient jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEvent event) {
        HttpServletRequest request = currentRequest();
        jdbc.sql("""
                        INSERT INTO audit_log (org_id, actor_id, action, target_type, target_id, outcome,
                                               ip, user_agent, request_id, details, occurred_at)
                        VALUES (:orgId, :actorId, :action, :targetType, :targetId, :outcome,
                                :ip, :userAgent, :requestId, CAST(:details AS jsonb), :occurredAt)
                        """)
                .param("orgId", event.orgId())
                .param("actorId", event.actorId())
                .param("action", event.action())
                .param("targetType", event.targetType())
                .param("targetId", event.targetId())
                .param("outcome", event.outcome().name())
                .param("ip", request != null ? request.getRemoteAddr() : null)
                .param("userAgent", request != null ? truncate(request.getHeader(HttpHeaders.USER_AGENT)) : null)
                .param("requestId", MDC.get(RequestIdFilter.MDC_KEY))
                .param("details", json.writeValueAsString(event.details()))
                .param("occurredAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .update();
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest()
                : null;
    }

    private static String truncate(String value) {
        return value == null || value.length() <= MAX_USER_AGENT ? value : value.substring(0, MAX_USER_AGENT);
    }
}
