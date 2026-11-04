-- Baseline migration.
-- Roles, the "app" schema and the pgvector/citext extensions are created by
-- infra/postgres/init/01-init.sh (they need superuser rights the migrator does not have).
-- Module tables are added from V2 onwards, starting with the identity module in Phase 2.2.

COMMENT ON SCHEMA app IS 'FlowPilot AI application schema (managed by Flyway)';
