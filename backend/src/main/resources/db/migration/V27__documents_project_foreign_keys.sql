-- Ties documents and AI conversations to real projects of the same organization.
-- V20 and V21 could not declare these foreign keys because the project table did not exist yet
-- (it arrives with V25). The composite key (project_id, org_id) -> project (id, org_id) makes the
-- database itself refuse a row that points at a project that does not exist, or at a project that
-- belongs to another organization. Deleting a project cascades to everything attached to it.
--
-- document_chunk is also covered: it carries (project_id, org_id) like its parent document.

ALTER TABLE document
    ADD CONSTRAINT fk_document_project FOREIGN KEY (project_id, org_id)
        REFERENCES project (id, org_id) ON DELETE CASCADE;

ALTER TABLE document_chunk
    ADD CONSTRAINT fk_chunk_project FOREIGN KEY (project_id, org_id)
        REFERENCES project (id, org_id) ON DELETE CASCADE;

ALTER TABLE ai_conversation
    ADD CONSTRAINT fk_ai_conversation_project FOREIGN KEY (project_id, org_id)
        REFERENCES project (id, org_id) ON DELETE CASCADE;
