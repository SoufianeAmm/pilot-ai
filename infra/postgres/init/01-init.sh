#!/bin/sh
# Runs once, when the PostgreSQL data volume is first created
# (official image: /docker-entrypoint-initdb.d). Also used by Testcontainers.
#
# Least-privilege layout:
#   flowpilot_migrator  owns schema "app", runs Flyway (DDL)
#   flowpilot_app       used by the application: data access only, no DDL
#   schema "extensions" holds pgvector/citext so neither role needs superuser
set -eu

: "${FLOWPILOT_DB_MIGRATOR_PASSWORD:?FLOWPILOT_DB_MIGRATOR_PASSWORD must be set}"
: "${FLOWPILOT_DB_APP_PASSWORD:?FLOWPILOT_DB_APP_PASSWORD must be set}"

psql -v ON_ERROR_STOP=1 \
     --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v migrator_pw="$FLOWPILOT_DB_MIGRATOR_PASSWORD" \
     -v app_pw="$FLOWPILOT_DB_APP_PASSWORD" <<'SQL'
CREATE ROLE flowpilot_migrator LOGIN PASSWORD :'migrator_pw';
CREATE ROLE flowpilot_app      LOGIN PASSWORD :'app_pw';

REVOKE ALL     ON DATABASE :"DBNAME" FROM PUBLIC;
GRANT  CONNECT ON DATABASE :"DBNAME" TO flowpilot_migrator, flowpilot_app;
REVOKE ALL     ON SCHEMA public FROM PUBLIC;

CREATE SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS vector SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS citext SCHEMA extensions;
GRANT USAGE ON SCHEMA extensions TO flowpilot_migrator, flowpilot_app;

CREATE SCHEMA app AUTHORIZATION flowpilot_migrator;
GRANT USAGE ON SCHEMA app TO flowpilot_app;

-- Tables and sequences the migrator creates later are automatically usable by the app role
-- (DML only). Individual migrations can revoke further, e.g. UPDATE/DELETE on audit_log.
ALTER DEFAULT PRIVILEGES FOR ROLE flowpilot_migrator IN SCHEMA app
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO flowpilot_app;
ALTER DEFAULT PRIVILEGES FOR ROLE flowpilot_migrator IN SCHEMA app
  GRANT USAGE, SELECT ON SEQUENCES TO flowpilot_app;

ALTER ROLE flowpilot_migrator SET search_path = app, extensions;
ALTER ROLE flowpilot_app      SET search_path = app, extensions;
SQL
