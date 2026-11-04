-- AI module: conversations, messages, and the tool calls made for each answer.
-- org_id / project_id have no foreign keys yet (organization and project tables do not exist).

CREATE TABLE ai_conversation (
    id          uuid         PRIMARY KEY,
    org_id      uuid         NOT NULL,
    project_id  uuid         NOT NULL,
    user_id     uuid         NOT NULL REFERENCES app_user (id),
    title       varchar(200) NOT NULL,
    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL,
    version     bigint       NOT NULL,
    CONSTRAINT uq_ai_conversation_id_org UNIQUE (id, org_id)
);
CREATE INDEX ix_ai_conversation_user_project
    ON ai_conversation (user_id, project_id, updated_at DESC);

CREATE TABLE ai_message (
    id                  uuid         PRIMARY KEY,
    org_id              uuid         NOT NULL,
    conversation_id     uuid         NOT NULL,
    role                varchar(16)  NOT NULL,
    content             text         NOT NULL,
    structured_response jsonb,
    status              varchar(20)  NOT NULL,
    model               varchar(100),
    prompt_tokens       integer,
    completion_tokens   integer,
    latency_ms          integer,
    verification        jsonb        NOT NULL DEFAULT '{}'::jsonb,
    created_at          timestamptz  NOT NULL,
    CONSTRAINT fk_ai_message_conversation FOREIGN KEY (conversation_id, org_id)
        REFERENCES ai_conversation (id, org_id) ON DELETE CASCADE,
    CONSTRAINT uq_ai_message_id_org UNIQUE (id, org_id),
    CONSTRAINT ck_ai_message_role CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT ck_ai_message_status
        CHECK (status IN ('OK', 'INVALID_OUTPUT', 'TIMEOUT', 'REJECTED', 'ERROR'))
);
CREATE INDEX ix_ai_message_conversation ON ai_message (conversation_id, created_at);

CREATE TABLE ai_tool_invocation (
    id            uuid         PRIMARY KEY,
    org_id        uuid         NOT NULL,
    message_id    uuid         NOT NULL,
    tool_name     varchar(100) NOT NULL,
    arguments     jsonb        NOT NULL DEFAULT '{}'::jsonb,
    returned_refs jsonb        NOT NULL DEFAULT '[]'::jsonb,
    authorized    boolean      NOT NULL,
    duration_ms   integer,
    created_at    timestamptz  NOT NULL,
    CONSTRAINT fk_ai_tool_message FOREIGN KEY (message_id, org_id)
        REFERENCES ai_message (id, org_id) ON DELETE CASCADE
);
CREATE INDEX ix_ai_tool_invocation_message ON ai_tool_invocation (message_id);