# Development Guide

## Prerequisites
- Docker Desktop (running)
- Node.js 24 (frontend)
- *Optional:* JDK 21, to run the backend outside Docker or get full IDE support. Without it, use the Docker commands below.
- Maven is **not** required. The repository ships the Maven Wrapper (`backend/mvnw`).

## First-time setup
```powershell
copy .env.example .env        # then replace every "change-me" with a random password
cd frontend; npm install; cd ..
```

## Run the stack (database, migrations, API)
```powershell
docker compose up --build -d
docker compose ps             # migrate: "Exited (0)", app: "healthy"
curl http://127.0.0.1:8090/api/v1/meta
```
Startup order: `db` becomes healthy → `migrate` applies Flyway migrations and exits with code 0 → `app` starts.

Stop it with `docker compose down`. Add `-v` to also **delete the database volume**, which re-runs `infra/postgres/init` on the next start.

## Run the frontend
```powershell
cd frontend
npm run dev                   # http://localhost:5173, proxies /api to http://localhost:8090
```

## Tests
| What | Command |
|---|---|
| Frontend lint, tests, build | `cd frontend; npm run lint; npm test; npm run build` |
| Backend, with JDK 21 installed | `cd backend; .\mvnw verify` |
| Backend, without a local JDK (Docker) | see below |

```powershell
docker run --rm -v "${PWD}:/workspace" -v flowpilot-m2:/root/.m2 `
  -v /var/run/docker.sock:/var/run/docker.sock `
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal `
  -w /workspace/backend maven:3.9.16-eclipse-temurin-21 mvn -B -ntp verify
```
`verify` runs the unit tests (`*Test`, `*Tests`) and then the integration tests (`*IT`). The integration tests start a real PostgreSQL + pgvector container through Testcontainers.

## Ports
| Port | Service | Exposed |
|---|---|---|
| 5173 | Vite dev server | localhost |
| 8090 | API (`FLOWPILOT_HTTP_PORT` in `.env`) | localhost only, until Nginx arrives in Phase 5 |
| 8081 | Actuator (health, metrics) | inside Docker only |
| 5432 | PostgreSQL | localhost only |

## Database roles
| Role | Used by | Rights |
|---|---|---|
| `postgres` | the database container only | superuser |
| `flowpilot_migrator` | `migrate` service (Flyway) | owns schema `app` (DDL) |
| `flowpilot_app` | the application | `SELECT/INSERT/UPDATE/DELETE` only, no DDL |

`DatabaseRolesIT` verifies that the application role cannot change the schema.

## Troubleshooting
**`docker pull` fails with "TLS handshake timeout" on `production.cloudfront.docker.com`**
Some networks block Docker Hub's download server. Pull through Google's mirror and re-tag the image:
```powershell
docker pull mirror.gcr.io/pgvector/pgvector:pg17
docker tag  mirror.gcr.io/pgvector/pgvector:pg17 pgvector/pgvector:pg17
```
Official images use the `library/` prefix, e.g. `mirror.gcr.io/library/eclipse-temurin:21-jre-noble`. For a permanent fix, add `"registry-mirrors": ["https://mirror.gcr.io"]` under Docker Desktop → Settings → Docker Engine.

**`Bind for 0.0.0.0:8090 failed: port is already allocated`**
Another program is using the port. Set `FLOWPILOT_HTTP_PORT=8095` (or any free port) in `.env`.
