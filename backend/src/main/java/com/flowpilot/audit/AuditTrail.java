package com.flowpilot.audit;

/**
 * Records security-relevant operations. Every module uses this single entry point.
 *
 * <p>The entry is written in its own transaction, so DENIED and FAILURE events survive a
 * rollback of the caller's transaction. Never put passwords, tokens, API keys or document
 * text into the event details.
 */
public interface AuditTrail {

    void record(AuditEvent event);
}
