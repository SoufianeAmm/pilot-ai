-- Projects module: projects, project memberships (project roles) and milestones.
-- Depends on V24__organizations.sql (organization, app_user).
-- The "key" column is the per-org human prefix (e.g. "FP" in "FP-42"); next_task_number is the
-- per-project sequence that produces human-readable task keys (assigned under a row lock).
-- Tasks, dependencies and comments are added by later migrations (V26, V27).
--
-- org_id is denormalized onto project_member and milestone so every child carries the tenant and
-- can use a composite foreign key (project_id, org_id) -> project(id, org_id). That makes it
-- impossible to attach a milestone or member to a project from another organization.

CREATE TABLE project (
    id               uuid         PRIMARY KEY,
    org_id           uuid         NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    key              varchar(10)  NOT NULL,
    name             varchar(120) NOT NULL,
    description      text,
    status           varchar(16)  NOT NULL,
    start_date       date,
    target_date      date,
    owner_id         uuid         NOT NULL REFERENCES app_user (id),
    next_task_number integer      NOT NULL,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    version          bigint       NOT NULL,
    -- composite unique so (id, org_id) can be the target of child composite foreign keys
    CONSTRAINT uq_project_id_org UNIQUE (id, org_id),
    -- the human key is unique within an organization (e.g. only one "FP" per org)
    CONSTRAINT uq_project_org_key UNIQUE (org_id, key),
    CONSTRAINT ck_project_key CHECK (key ~ '^[A-Z][A-Z0-9]{1,9}$'),
    CONSTRAINT ck_project_status CHECK (status IN ('PLANNED', 'ACTIVE', 'ON_HOLD', 'COMPLETED', 'ARCHIVED')),
    CONSTRAINT ck_project_next_task CHECK (next_task_number >= 1),
    CONSTRAINT ck_project_dates CHECK (target_date IS NULL OR start_date IS NULL OR target_date >= start_date)
);
CREATE INDEX ix_project_org_status ON project (org_id, status);
CREATE INDEX ix_project_org_updated ON project (org_id, updated_at DESC);

-- Composite primary key (project_id, user_id): a user is a member of a project at most once.
-- org_id is redundant with the project's org_id but required for the composite foreign key.
CREATE TABLE project_member (
    project_id             uuid        NOT NULL,
    user_id                uuid        NOT NULL,
    org_id                 uuid        NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    role                   varchar(16) NOT NULL,
    weekly_capacity_points integer     NOT NULL,
    joined_at              timestamptz NOT NULL,
    version                bigint      NOT NULL,
    PRIMARY KEY (project_id, user_id),
    -- composite FK: cannot point a member at a project from another organization
    CONSTRAINT fk_project_member_project FOREIGN KEY (project_id, org_id)
        REFERENCES project (id, org_id) ON DELETE CASCADE,
    CONSTRAINT fk_project_member_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_project_member_role CHECK (role IN ('MANAGER', 'CONTRIBUTOR', 'VIEWER')),
    CONSTRAINT ck_project_member_capacity CHECK (weekly_capacity_points >= 0)
);
-- "which projects is this user on?" and workload queries
CREATE INDEX ix_project_member_user ON project_member (user_id);

CREATE TABLE milestone (
    id          uuid         PRIMARY KEY,
    org_id      uuid         NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    project_id  uuid         NOT NULL,
    number      integer      NOT NULL,
    name        varchar(120) NOT NULL,
    due_date    date,
    status      varchar(16)  NOT NULL,
    created_at  timestamptz  NOT NULL,
    version     bigint       NOT NULL,
    CONSTRAINT fk_milestone_project FOREIGN KEY (project_id, org_id)
        REFERENCES project (id, org_id) ON DELETE CASCADE,
    -- milestone numbers are "M-n", unique within a project
    CONSTRAINT uq_milestone_project_number UNIQUE (project_id, number),
    CONSTRAINT ck_milestone_status CHECK (status IN ('OPEN', 'COMPLETED'))
);
-- upcoming-deadline and open-milestone queries
CREATE INDEX ix_milestone_project_due ON milestone (project_id, due_date) WHERE status = 'OPEN';
