-- Organizations module: tenants, memberships (organization roles) and invitations.
-- Foreign keys from other modules' org_id columns to organization(id) are added by those modules.

CREATE TABLE organization (
    id         uuid         PRIMARY KEY,
    name       varchar(100) NOT NULL,
    slug       varchar(60)  NOT NULL,
    time_zone  varchar(64)  NOT NULL,
    created_at timestamptz  NOT NULL,
    version    bigint       NOT NULL,
    CONSTRAINT uq_organization_slug UNIQUE (slug),
    CONSTRAINT ck_organization_slug CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE TABLE organization_membership (
    id        uuid        PRIMARY KEY,
    org_id    uuid        NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    user_id   uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role      varchar(20) NOT NULL,
    joined_at timestamptz NOT NULL,
    version   bigint      NOT NULL,
    CONSTRAINT uq_membership_org_user UNIQUE (org_id, user_id),
    CONSTRAINT ck_membership_role CHECK (role IN ('OWNER', 'ADMIN', 'PROJECT_MANAGER', 'MEMBER'))
);
-- "which organizations is this user in?" runs on every login and org switch
CREATE INDEX ix_membership_user ON organization_membership (user_id);

-- Only the SHA-256 hash of an invitation token is stored; the raw token travels in the invitation link.
CREATE TABLE invitation (
    id          uuid         PRIMARY KEY,
    org_id      uuid         NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    email       varchar(254) NOT NULL,
    role        varchar(20)  NOT NULL,
    token_hash  bytea        NOT NULL,
    invited_by  uuid         NOT NULL REFERENCES app_user (id),
    created_at  timestamptz  NOT NULL,
    expires_at  timestamptz  NOT NULL,
    accepted_at timestamptz,
    revoked_at  timestamptz,
    version     bigint       NOT NULL,
    CONSTRAINT uq_invitation_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_invitation_email_normalized CHECK (email = lower(btrim(email))),
    CONSTRAINT ck_invitation_role CHECK (role IN ('OWNER', 'ADMIN', 'PROJECT_MANAGER', 'MEMBER'))
);
-- at most one open invitation per email and organization (re-inviting revokes the old one first)
CREATE UNIQUE INDEX uq_invitation_open ON invitation (org_id, email)
    WHERE accepted_at IS NULL AND revoked_at IS NULL;
