# 04 — Repository Structure and Conventions

```
flowpilot/                              (repo root = d:\AI project)
├── README.md
├── .env.example                        # every config key, no real secrets
├── .gitignore  .gitattributes  .editorconfig
├── docker-compose.yml                  # db, migrate, app (replicas), nginx
├── docker-compose.monitoring.yml       # prometheus + grafana (optional overlay)
├── .github/workflows/
│   ├── ci.yml                          # backend + frontend + image build
│   └── eval.yml                        # manual AI evaluation run (needs API key secret)
├── docs/
│   ├── 01-architecture.md … 05-implementation-plan.md
│   ├── adr/                            # Architecture Decision Records (short, numbered)
│   ├── api/                            # exported openapi.json snapshot
│   └── report/                         # academic report outline + figures
├── infra/
│   ├── nginx/nginx.conf, conf.d/flowpilot.conf
│   ├── postgres/init/                  # create extension vector, roles (app vs migrator)
│   ├── prometheus/prometheus.yml
│   └── grafana/provisioning/…
├── backend/
│   ├── mvnw, mvnw.cmd, .mvn/
│   ├── pom.xml
│   ├── Dockerfile                      # multi-stage, layered jar, non-root user
│   └── src/
│       ├── main/java/com/flowpilot/
│       │   ├── FlowPilotApplication.java
│       │   ├── shared/                 # cross-cutting, no business logic
│       │   │   ├── security/           # SecurityConfig, JwtFilter, CurrentUser
│       │   │   ├── web/                # ProblemDetails handler, paging, RequestIdFilter
│       │   │   ├── ratelimit/          # Bucket4j setup
│       │   │   └── time/               # Clock bean (testable time)
│       │   ├── identity/
│       │   │   ├── IdentityApi.java          # public module API
│       │   │   ├── UserRegisteredEvent.java  # public events
│       │   │   └── internal/
│       │   │       ├── web/            # AuthController + request/response DTOs
│       │   │       ├── application/    # AuthService, TokenService
│       │   │       ├── domain/         # User, RefreshToken (JPA entities)
│       │   │       └── persistence/    # Spring Data repositories
│       │   ├── organizations/  (same layout)
│       │   ├── projects/       ProjectAccess.java, ProjectScope.java, Permission.java …
│       │   ├── tasks/          TaskQueries.java …
│       │   ├── collaboration/
│       │   ├── documents/      DocumentStorage.java (interface), KnowledgeSearch.java …
│       │   ├── ai/             internal/tools/ProjectAnalystTools.java, internal/evidence/…
│       │   ├── analytics/      ProjectMetrics.java, HealthScoreCalculator …
│       │   ├── notifications/
│       │   └── audit/
│       ├── main/resources/
│       │   ├── application.yml, application-dev.yml, application-test.yml
│       │   ├── db/migration/V1__identity.sql, V2__organizations.sql …
│       │   └── prompts/analyst-system.st, knowledge-system.st
│       └── test/java/com/flowpilot/
│           ├── ModularityTests.java    # ApplicationModules.verify()
│           ├── support/                # Testcontainers base, fixtures, FakeChatModel
│           └── <module>/…              # unit + integration tests per module
├── frontend/
│   ├── package.json, vite.config.ts, tsconfig.json, tailwind config, eslint config
│   ├── Dockerfile                      # build stage → static files copied into nginx image
│   ├── src/
│   │   ├── app/                        # router, providers, layout, auth guard
│   │   ├── api/                        # typed client (generated from OpenAPI), query keys
│   │   ├── auth/                       # in-memory token store, refresh logic
│   │   ├── features/
│   │   │   ├── organizations/ projects/ tasks/ board/
│   │   │   ├── analyst/                # chat, evidence panel, reference links
│   │   │   ├── knowledge/              # upload, status, Q&A with sources
│   │   │   ├── dashboard/              # Recharts: "Calculated" vs "AI interpretation"
│   │   │   └── notifications/
│   │   ├── components/ui/              # shadcn/ui primitives
│   │   └── lib/                        # ws client, formatting, utils
│   ├── tests/                          # Vitest + Testing Library + MSW
│   └── e2e/                            # Playwright specs
├── evaluation/
│   ├── dataset/                        # seed scenario + questions.jsonl with expected facts & refs
│   ├── fixtures/                       # PDFs incl. prompt-injection documents
│   └── runner/                         # JUnit-tagged eval runner producing results/*.json + report.md
└── loadtest/
    ├── k6/                             # scenarios: crud-mix, ai-burst, failover, rate-limit
    └── results/                        # committed summaries of measured runs
```

## Conventions

**Backend**
- Package by **module, then layer** (`tasks/internal/web`, `…/application`, …). Only a module's root package is public.
- DTOs are Java `record`s. Entities never leave the `internal` package and are never serialized.
- Controllers stay thin: validate the input (`@Valid`), resolve the `ProjectScope`, and delegate.
- Services that touch project data take a `ProjectScope` parameter.
- Mapping is hand-written (small static mappers). There is no MapStruct, which keeps the build simpler; it can be revisited later.
- Errors use RFC 9457 `ProblemDetail` with a stable `type` URI per error kind (`/problems/conflict`, `/problems/rate-limited`, …).
- Time is always taken from an injected `Clock`, so tests can control it.
- Lombok is not used. Records and IDE generation cover the need, and the build stays more transparent.
- Tests are named `MethodOrBehavior_condition_expectedResult`. Integration tests end in `IT` and run under Failsafe.

**Frontend**
- Server state lives in TanStack Query, which is the only source of truth for API data. The only client state is UI state and the in-memory access token.
- The API client is typed from the OpenAPI spec (`openapi-typescript`), so frontend and backend contracts cannot silently drift.
- Feature folders own their components, hooks and tests.

**Git**
- Trunk-based: short-lived branches with PRs into `main`, and CI must pass before merging.
- Conventional Commits (`feat(tasks): …`, `fix(ai): …`).

## API conventions (contract preview — full OpenAPI generated in Phase 2)

Base path `/api/v1`. JSON. Pagination uses `?page=0&size=20&sort=dueDate,asc` (size ≤ 100, sort fields whitelisted).

| Area | Endpoints |
|---|---|
| Auth | `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`, `GET /me` |
| Orgs | `GET/POST /orgs`, `GET/PATCH /orgs/{orgId}`, `GET /orgs/{orgId}/members`, `PATCH/DELETE /orgs/{orgId}/members/{userId}`, `POST /orgs/{orgId}/invitations`, `POST /invitations/accept` |
| Projects | `GET/POST /orgs/{orgId}/projects`, `GET/PATCH/DELETE /orgs/{orgId}/projects/{projectId}`, `…/members`, `…/milestones[/{milestoneId}]` |
| Tasks | `GET/POST …/projects/{projectId}/tasks`, `GET/PATCH/DELETE …/tasks/{taskId}`, `PUT/DELETE …/tasks/{taskId}/dependencies/{dependsOnId}`, `GET/POST …/tasks/{taskId}/comments` |
| Activity | `GET …/projects/{projectId}/activity` |
| Analytics | `GET …/projects/{projectId}/analytics/summary`, `…/analytics/trend?days=30`, `…/analytics/workload`, `GET /orgs/{orgId}/analytics/portfolio` |
| AI | `POST …/projects/{projectId}/ai/conversations`, `GET …/ai/conversations`, `POST …/ai/conversations/{id}/messages`, `POST …/ai/weekly-summary` |
| Documents | `POST …/projects/{projectId}/documents` (multipart), `GET …/documents`, `GET/DELETE …/documents/{documentId}`, `POST …/knowledge/query` |
| Notifications | `GET /me/notifications`, `POST /me/notifications/{id}/read` |
| Audit | `GET /orgs/{orgId}/audit` |
| WebSocket | `/ws` (STOMP). Auth token sent in the CONNECT frame. Subscribing to `/topic/projects/{projectId}` is authorized per project. |

`…` = `/api/v1/orgs/{orgId}`. Updates use `PATCH` with a required `version` field. A stale version returns **409** with the current representation.
