package com.flowpilot.audit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One audit entry. Build it with the factories and withers, for example:
 * {@code AuditEvent.success(AuditActions.PROJECT_DELETED, scope.orgId(), user.userId()).withTarget("project", id)}.
 *
 * @param orgId   organization the action belongs to, or null (e.g. login)
 * @param actorId user who performed the action, or null when unknown (e.g. failed login)
 * @param details small, non-sensitive context values (no passwords, tokens or document text)
 */
public record AuditEvent(String action, AuditOutcome outcome, UUID orgId, UUID actorId,
                         String targetType, UUID targetId, Map<String, Object> details) {

    public AuditEvent {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(outcome, "outcome");
        details = details == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public static AuditEvent success(String action, UUID orgId, UUID actorId) {
        return new AuditEvent(action, AuditOutcome.SUCCESS, orgId, actorId, null, null, Map.of());
    }

    public static AuditEvent denied(String action, UUID orgId, UUID actorId) {
        return new AuditEvent(action, AuditOutcome.DENIED, orgId, actorId, null, null, Map.of());
    }

    public static AuditEvent failure(String action, UUID orgId, UUID actorId) {
        return new AuditEvent(action, AuditOutcome.FAILURE, orgId, actorId, null, null, Map.of());
    }

    public AuditEvent withTarget(String type, UUID id) {
        return new AuditEvent(action, outcome, orgId, actorId, type, id, details);
    }

    public AuditEvent withDetail(String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(details);
        copy.put(key, value);
        return new AuditEvent(action, outcome, orgId, actorId, targetType, targetId, copy);
    }
}
