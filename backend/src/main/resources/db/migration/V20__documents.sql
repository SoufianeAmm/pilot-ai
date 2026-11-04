-- Documents module: uploaded files, extracted text chunks with embeddings, ingestion queue.
-- org_id / project_id have no foreign keys yet: the organization and project tables do not exist.
-- A later migration adds composite FKs once they are merged.

CREATE TABLE document (
    id            uuid          PRIMARY KEY,
    org_id        uuid          NOT NULL,
    project_id    uuid          NOT NULL,
    filename      varchar(255)  NOT NULL,
    content_type  varchar(100)  NOT NULL,
    size_bytes    bigint        NOT NULL,
    sha256        bytea         NOT NULL,
    storage_key   varchar(255)  NOT NULL,
    status        varchar(16)   NOT NULL,
    error_message varchar(500),
    page_count    integer,
    uploaded_by   uuid          NOT NULL REFERENCES app_user (id),
    created_at    timestamptz   NOT NULL,
    updated_at    timestamptz   NOT NULL,
    deleted_at    timestamptz,
    version       bigint        NOT NULL,
    CONSTRAINT uq_document_id_org UNIQUE (id, org_id),
    CONSTRAINT uq_document_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_document_status CHECK (status IN ('PENDING','PROCESSING','READY','FAILED')),
    CONSTRAINT ck_document_size CHECK (size_bytes > 0),
    CONSTRAINT ck_document_sha256 CHECK (octet_length(sha256) = 32)
);
CREATE UNIQUE INDEX uq_document_project_sha256 ON document (project_id, sha256) WHERE deleted_at IS NULL;
CREATE INDEX ix_document_project_created ON document (project_id, created_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE document_chunk (
    id              uuid        PRIMARY KEY,
    org_id          uuid        NOT NULL,
    project_id      uuid        NOT NULL,
    document_id     uuid        NOT NULL,
    chunk_index     integer     NOT NULL,
    page_from       integer,
    page_to         integer,
    section_title   varchar(255),
    content         text        NOT NULL,
    token_count     integer     NOT NULL,
    embedding       extensions.vector(768) NOT NULL,
    embedding_model varchar(100) NOT NULL,
    CONSTRAINT fk_chunk_document FOREIGN KEY (document_id, org_id)
        REFERENCES document (id, org_id) ON DELETE CASCADE,
    CONSTRAINT uq_chunk_document_index UNIQUE (document_id, chunk_index)
);
CREATE INDEX ix_chunk_project ON document_chunk (project_id);
CREATE INDEX ix_chunk_embedding ON document_chunk
    USING hnsw (embedding extensions.vector_cosine_ops);

CREATE TABLE ingestion_job (
    id           uuid         PRIMARY KEY,
    document_id  uuid         NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    status       varchar(16)  NOT NULL,
    attempts     integer      NOT NULL,
    next_run_at  timestamptz  NOT NULL,
    locked_until timestamptz,
    locked_by    varchar(100),
    last_error   varchar(500),
    created_at   timestamptz  NOT NULL,
    updated_at   timestamptz  NOT NULL,
    version      bigint       NOT NULL,
    CONSTRAINT uq_ingestion_job_document UNIQUE (document_id),
    CONSTRAINT ck_ingestion_job_status CHECK (status IN ('PENDING','RUNNING','DONE','FAILED'))
);
CREATE INDEX ix_ingestion_job_poll ON ingestion_job (status, next_run_at);