-- Identity module: users, refresh tokens (rotation + reuse detection), login attempt counters.

CREATE TABLE app_user (
    id            uuid         PRIMARY KEY,
    email         varchar(254) NOT NULL,
    password_hash varchar(255) NOT NULL,
    display_name  varchar(80)  NOT NULL,
    status        varchar(16)  NOT NULL,
    created_at    timestamptz  NOT NULL,
    version       bigint       NOT NULL,
    CONSTRAINT uq_app_user_email UNIQUE (email),
    -- the application stores emails trimmed and lower-cased, which makes the unique constraint case-insensitive
    CONSTRAINT ck_app_user_email_normalized CHECK (email = lower(btrim(email))),
    CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

-- Only the SHA-256 hash of a refresh token is stored; the raw value exists only in the user's cookie.
CREATE TABLE refresh_token (
    id             uuid         PRIMARY KEY,
    user_id        uuid         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash     bytea        NOT NULL,
    family_id      uuid         NOT NULL,
    expires_at     timestamptz  NOT NULL,
    revoked_at     timestamptz,
    replaced_by_id uuid,
    created_at     timestamptz  NOT NULL,
    user_agent     varchar(255),
    version        bigint       NOT NULL,
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);
CREATE INDEX ix_refresh_token_family ON refresh_token (family_id);
CREATE INDEX ix_refresh_token_user ON refresh_token (user_id);

-- Failed-login counters shared by all replicas (fixed window per key).
CREATE TABLE login_attempt (
    attempt_key  varchar(400) PRIMARY KEY,
    failures     integer      NOT NULL,
    window_start timestamptz  NOT NULL
);
