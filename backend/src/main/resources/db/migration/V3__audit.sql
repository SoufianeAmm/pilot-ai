-- Audit module: append-only log of security-relevant operations.

CREATE TABLE audit_log (
    id          bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    org_id      uuid,
    actor_id    uuid,
    action      varchar(100) NOT NULL,
    target_type varchar(50),
    target_id   uuid,
    outcome     varchar(16)  NOT NULL,
    ip          varchar(45),
    user_agent  varchar(255),
    request_id  varchar(64),
    details     jsonb        NOT NULL DEFAULT '{}'::jsonb,
    occurred_at timestamptz  NOT NULL,
    CONSTRAINT ck_audit_log_outcome CHECK (outcome IN ('SUCCESS', 'DENIED', 'FAILURE'))
);
CREATE INDEX ix_audit_log_org_time ON audit_log (org_id, occurred_at DESC);
CREATE INDEX ix_audit_log_actor_time ON audit_log (actor_id, occurred_at DESC);

-- The application role received full DML through default privileges; audit entries must be immutable.
REVOKE UPDATE, DELETE, TRUNCATE ON audit_log FROM flowpilot_app;
