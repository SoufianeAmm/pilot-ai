-- AI analysis history: stored results of risk analyses, status summaries and weekly summaries.
-- org_id / project_id have no foreign keys yet (organization and project tables do not exist).

CREATE TABLE ai_analysis (
    id            uuid         PRIMARY KEY,
    org_id        uuid         NOT NULL,
    project_id    uuid         NOT NULL,
    requested_by  uuid         NOT NULL REFERENCES app_user (id),
    type          varchar(24)  NOT NULL,
    status        varchar(20)  NOT NULL,
    result        jsonb,
    evidence      jsonb        NOT NULL DEFAULT '[]'::jsonb,
    verification  jsonb        NOT NULL DEFAULT '{}'::jsonb,
    model         varchar(100),
    prompt_tokens     integer,
    completion_tokens integer,
    latency_ms    integer,
    created_at    timestamptz  NOT NULL,
    CONSTRAINT uq_ai_analysis_id_org UNIQUE (id, org_id),
    CONSTRAINT ck_ai_analysis_type
        CHECK (type IN ('RISK_ANALYSIS', 'PROJECT_STATUS', 'WEEKLY_SUMMARY')),
    CONSTRAINT ck_ai_analysis_status
        CHECK (status IN ('OK', 'INVALID_OUTPUT', 'TIMEOUT', 'REJECTED', 'ERROR'))
);
CREATE INDEX ix_ai_analysis_project_time ON ai_analysis (project_id, created_at DESC);
CREATE INDEX ix_ai_analysis_requested_by ON ai_analysis (requested_by);