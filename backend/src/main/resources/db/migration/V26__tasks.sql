-- Tasks module: tasks and their dependencies.
-- Depends on V25__projects.sql (project, milestone, app_user).
--
-- The human task key "FP-42" is project.key + "-" + task.number. The number is assigned under a
-- row lock on the project (see ProjectQueries.reserveNextTaskNumber) so two concurrent creates
-- never get the same number.
--
-- "blocked" and "overdue" are NOT stored columns: they are derived in queries/Java (see docs
-- 03-data-model "Derived, not stored"). A task is blocked when it is open and has an unfinished
-- dependency; overdue when due_date < today and the status is not terminal.
--
-- org_id is denormalized onto both tables so children use composite foreign keys
-- (project_id, org_id) -> project(id, org_id), making cross-tenant attachment impossible.

-- milestone needs UNIQUE(id, org_id) so task can reference it via a composite FK.
-- (project already has it from V25; milestone only had PK(id) + UNIQUE(project_id, number).)
ALTER TABLE milestone ADD CONSTRAINT uq_milestone_id_org UNIQUE (id, org_id);

CREATE TABLE task (
    id            uuid         PRIMARY KEY,
    org_id        uuid         NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    project_id    uuid         NOT NULL,
    number        integer      NOT NULL,
    title         varchar(200) NOT NULL,
    description   text,
    status        varchar(16)  NOT NULL,
    priority      varchar(16)  NOT NULL,
    assignee_id   uuid         REFERENCES app_user (id),
    reporter_id   uuid         NOT NULL REFERENCES app_user (id),
    milestone_id  uuid,
    due_date      date,
    estimate_points integer,
    started_at    timestamptz,
    completed_at  timestamptz,
    deleted_at    timestamptz,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    version       bigint       NOT NULL,
    CONSTRAINT fk_task_project FOREIGN KEY (project_id, org_id)
        REFERENCES project (id, org_id) ON DELETE CASCADE,
    -- single-column FK: ON DELETE SET NULL only nulls milestone_id (nullable), not org_id (NOT NULL).
    -- A composite FK would null both columns and violate the NOT NULL org_id on milestone delete.
    CONSTRAINT fk_task_milestone FOREIGN KEY (milestone_id)
        REFERENCES milestone (id) ON DELETE SET NULL,
    CONSTRAINT uq_task_project_number UNIQUE (project_id, number),
    -- composite-FK target for task_dependency (task_id, org_id) -> task(id, org_id)
    CONSTRAINT uq_task_id_org UNIQUE (id, org_id),
    CONSTRAINT ck_task_status CHECK (status IN ('TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE', 'CANCELLED')),
    CONSTRAINT ck_task_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_task_estimate CHECK (estimate_points IS NULL OR estimate_points >= 0),
    -- a completed task records when it was completed
    CONSTRAINT ck_task_completion CHECK (
        (status IN ('DONE', 'CANCELLED')) = (completed_at IS NOT NULL)
    )
);
-- board / list queries, only over non-deleted tasks
CREATE INDEX ix_task_project_status ON task (project_id, status) WHERE deleted_at IS NULL;
-- overdue query: open tasks by due date
CREATE INDEX ix_task_project_due ON task (project_id, due_date)
    WHERE deleted_at IS NULL AND status NOT IN ('DONE', 'CANCELLED');
-- workload: tasks assigned to a user
CREATE INDEX ix_task_assignee ON task (assignee_id) WHERE deleted_at IS NULL;
-- dependencies look up the blocker
CREATE INDEX ix_task_milestone_lookup ON task (milestone_id) WHERE deleted_at IS NULL;

CREATE TABLE task_dependency (
    task_id            uuid NOT NULL,
    depends_on_task_id uuid NOT NULL,
    org_id             uuid NOT NULL REFERENCES organization (id) ON DELETE CASCADE,
    project_id         uuid NOT NULL,
    created_at  timestamptz NOT NULL,
    PRIMARY KEY (task_id, depends_on_task_id),
    CONSTRAINT fk_dependency_task FOREIGN KEY (task_id, org_id)
        REFERENCES task (id, org_id) ON DELETE CASCADE,
    CONSTRAINT fk_dependency_depends_on FOREIGN KEY (depends_on_task_id, org_id)
        REFERENCES task (id, org_id) ON DELETE CASCADE,
    -- self-loops rejected in the DB; longer cycles rejected in the service (DFS)
    CONSTRAINT ck_dependency_no_self CHECK (task_id <> depends_on_task_id)
);
-- "which tasks are blocked by X?" (reverse lookup, for cycle detection and unblock notifications)
CREATE INDEX ix_dependency_depends_on ON task_dependency (depends_on_task_id);