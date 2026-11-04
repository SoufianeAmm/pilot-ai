-- Analytics module: daily metric snapshots (for trends), weekly reports, and the lock table
-- used by ShedLock so that scheduled jobs run on a single replica.

CREATE TABLE project_metric_snapshot (
    project_id     uuid         NOT NULL,
    snapshot_date  date         NOT NULL,
    org_id         uuid         NOT NULL,
    total_tasks    integer      NOT NULL,
    done_tasks     integer      NOT NULL,
    overdue_tasks  integer      NOT NULL,
    blocked_tasks  integer      NOT NULL,
    health_score   integer      NOT NULL,
    created_at     timestamptz  NOT NULL,
    PRIMARY KEY (project_id, snapshot_date),
    CONSTRAINT ck_snapshot_counts CHECK (
        total_tasks >= 0 AND done_tasks >= 0 AND overdue_tasks >= 0 AND blocked_tasks >= 0
        AND done_tasks <= total_tasks),
    CONSTRAINT ck_snapshot_health CHECK (health_score BETWEEN 0 AND 100)
);
CREATE INDEX ix_snapshot_org_date ON project_metric_snapshot (org_id, snapshot_date DESC);

CREATE TABLE report (
    id            uuid         PRIMARY KEY,
    org_id        uuid         NOT NULL,
    project_id    uuid         NOT NULL,
    period_start  date         NOT NULL,
    period_end    date         NOT NULL,
    content       jsonb        NOT NULL,
    generated_by  varchar(16)  NOT NULL,
    analysis_id   uuid         REFERENCES ai_analysis (id) ON DELETE SET NULL,
    created_at    timestamptz  NOT NULL,
    CONSTRAINT ck_report_period CHECK (period_end >= period_start),
    CONSTRAINT ck_report_generated_by CHECK (generated_by IN ('SYSTEM', 'AI'))
);
CREATE INDEX ix_report_project_period ON report (project_id, period_end DESC);

-- Standard ShedLock (JDBC) table. Column names and types are fixed by the library.
CREATE TABLE shedlock (
    name       varchar(64)  PRIMARY KEY,
    lock_until timestamp(3) NOT NULL,
    locked_at  timestamp(3) NOT NULL,
    locked_by  varchar(255) NOT NULL
);