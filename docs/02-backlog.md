# 02 — Feature Backlog

Priority uses MoSCoW: **M** = must (required for the defense), **S** = should, **C** = could (stretch).
Phase = the implementation phase in [05-implementation-plan.md](05-implementation-plan.md).
Every story's acceptance criteria implicitly include: *authorized by the backend, tenant-scoped, covered by an automated test*.

## Personas
- **Olivia – Org owner:** sets up the organization, invites people, cares about cross-project health.
- **Paul – Project manager:** plans milestones, assigns work, asks the AI "what's at risk?".
- **Mia – Team member:** works on tasks, comments, checks her workload.
- **Victor – Viewer/stakeholder:** read-only; reads summaries and dashboards.

---

## EPIC A — Identity and access

| ID | Story | Pri | Phase | Acceptance criteria (key) |
|---|---|---|---|---|
| A1 | As a visitor I can register with email + password | M | 2 | Email unique (case-insensitive); password ≥ 12 chars; Argon2id hash; 201 without leaking whether email existed on conflict path timing |
| A2 | As a user I can log in and stay logged in across reloads | M | 2 | Access JWT 15 min; refresh cookie HttpOnly/Secure/SameSite=Strict; silent refresh |
| A3 | Refresh tokens rotate and reuse is detected | M | 2 | Reusing an old refresh token revokes the family → 401 |
| A4 | As a user I can log out (current device) | M | 2 | Refresh token revoked; cookie cleared |
| A5 | Login and registration are rate-limited | M | 2/5 | 5 failed logins / 15 min per account+IP → 429; shared across replicas |
| A6 | As an owner/admin I can invite by email with a role | M | 2 | Hashed single-use token, 7-day expiry; email delivery stubbed (logged link in dev) |
| A7 | As an invitee I can accept an invitation | M | 2 | Must be logged in with matching email; membership created once |
| A8 | As owner/admin I can change roles / remove members | M | 2 | Admin cannot modify owner; last owner cannot leave; audited |
| A9 | Sensitive operations are written to the audit log | M | 2 | Login success/failure, role change, member removal, project deletion, document deletion, AI tool denial |
| A10 | As owner I can view the audit log (paginated, filterable) | S | 2 | Org-scoped only |
| A11 | Password change, "log out all devices" | C | 6 | |

## EPIC B — Organizations and projects

| ID | Story | Pri | Phase | Acceptance criteria |
|---|---|---|---|---|
| B1 | Create organization (creator becomes OWNER) | M | 2 | Unique slug |
| B2 | List my organizations; switch active org in UI | M | 2 | Only memberships returned |
| B3 | Create project with key (e.g. `FP`), name, description, dates | M | 2 | Key unique per org, `^[A-Z][A-Z0-9]{1,9}$` |
| B4 | Edit/archive project; status PLANNED/ACTIVE/ON_HOLD/COMPLETED/ARCHIVED | M | 2 | Optimistic locking → 409 on stale version |
| B5 | Manage project members and project roles | M | 2 | Only org members can be added |
| B6 | Milestones: create/edit/complete, due dates | M | 2 | |
| B7 | Project list: search, filter by status, sort, paginate | M | 2 | Max page size 100; sort whitelist |
| B8 | Project health badge on list and detail | M | 3* | *Formula from analytics module; shown once analytics lands |

## EPIC C — Tasks

| ID | Story | Pri | Phase | Acceptance criteria |
|---|---|---|---|---|
| C1 | Create task with title, description, priority, due date, assignee, milestone | M | 2 | Human key `FP-42` via per-project sequence (row lock on project counter) |
| C2 | Update task fields and status (TODO → IN_PROGRESS → IN_REVIEW → DONE, CANCELLED) | M | 2 | `@Version`; 409 with current version on conflict |
| C3 | Delete task (soft delete) per permissions | M | 2 | Contributor may delete only own-reported tasks |
| C4 | Add/remove dependencies; cycles rejected | M | 2 | Same project only; DFS cycle check; 422 with cycle path |
| C5 | Blocked status derived from unfinished dependencies | M | 2 | Deterministic; not a stored flag that can drift |
| C6 | Overdue detection | M | 2 | `due_date < today(org timezone) AND status NOT IN (DONE, CANCELLED)` |
| C7 | Task list: filter (status, assignee, milestone, overdue, blocked), search, sort, paginate | M | 2 | No N+1 (verified by Hibernate statistics test) |
| C8 | Kanban board view | S | 2 | Drag → status update with optimistic locking |
| C9 | Workload summary per member | M | 2/3 | Open count, open estimate, overdue count |

## EPIC D — AI Project Analyst

| ID | Story | Pri | Phase | Acceptance criteria |
|---|---|---|---|---|
| D1 | Ask a natural-language question about a project | M | 3 | Response conforms to `AnalystResponse` schema |
| D2 | Tools: `getProjectOverview`, `getOverdueTasks`, `getBlockedTasks`, `getMilestoneStatus`, `getTeamWorkload`, `getRecentProjectActivity` | M | 3 | Read-only; scope bound server-side; args validated + capped |
| D3 | Every answer cites evidence references that resolve to real records | M | 3 | References ∉ evidence ledger are stripped and flagged |
| D4 | Clickable references open the task/milestone | M | 3 | |
| D5 | Conversation history persisted per user+project | M | 3 | Users can only see their own conversations |
| D6 | Clear errors for timeout, provider down, capacity exceeded, quota exceeded | M | 3 | 504/503/429 with Problem Details + `Retry-After` |
| D7 | Every tool call is authorized and logged (`ai_tool_invocation`) | M | 3 | Denials audited |
| D8 | Weekly AI project summary (on demand) | S | 3 | Uses same tools; labelled "AI interpretation" |
| D9 | Show "evidence used" panel (which tools ran, which records) | S | 3 | Transparency for the demo |
| D10 | Analyst can also use Knowledge Hub retrieval as a tool (`searchProjectDocuments`) | S | 4 | |
| D11 | Proposed actions with explicit user confirmation (write path via normal API) | C | 6 | LLM never executes writes |
| D12 | Streaming responses (SSE) | C | 6 | |

## EPIC E — Knowledge Hub (RAG)

| ID | Story | Pri | Phase | Acceptance criteria |
|---|---|---|---|---|
| E1 | Upload PDF to a project | M | 4 | ≤ 20 MB, magic-byte check, ≤ 300 pages, permission check |
| E2 | Asynchronous ingestion with visible status (PENDING/PROCESSING/READY/FAILED) | M | 4 | Works with any replica; retries bounded (3) |
| E3 | Ask a question over project documents | M | 4 | Answer + source list (document, page) |
| E4 | Delete document → file and chunks removed | M | 4 | Cascade; deleted chunks never retrieved |
| E5 | Duplicate upload detection (sha256 per project) | S | 4 | |
| E6 | Document-embedded instructions cannot change behavior or scope | M | 4 | Covered by injection eval cases |
| E7 | Scanned PDF (no text layer) reported clearly | S | 4 | FAILED with "no extractable text" |

## EPIC F — Analytics and risk

| ID | Story | Pri | Phase | Acceptance criteria |
|---|---|---|---|---|
| F1 | Project dashboard: completion, overdue, blocked, upcoming milestones | M | 3 | Computed by SQL; labelled "Calculated" |
| F2 | Health score with breakdown of contributing factors | M | 3 | Formula in docs; table-driven unit tests |
| F3 | Progress trend (daily snapshots) | M | 3 | ShedLock-guarded nightly job + on-demand backfill |
| F4 | Workload distribution chart | M | 3 | |
| F5 | AI risk explanation panel next to calculated metrics | M | 3 | Visually distinct; can be regenerated |
| F6 | Org-level portfolio overview | S | 3 | |

## EPIC G — Collaboration and notifications

| ID | Story | Pri | Phase | Acceptance criteria |
|---|---|---|---|---|
| G1 | Comment on tasks (edit/delete own) | M | 2 | Plain text/Markdown rendered safely (no raw HTML) |
| G2 | Project activity feed | M | 2 | Built from domain events |
| G3 | In-app notifications (assigned, mentioned, comment on my task, invitation) | S | 2 | |
| G4 | Real-time updates (board refresh, new notification) via WebSocket | S | 5 | Cross-replica via LISTEN/NOTIFY; JWT-authenticated handshake; per-project subscription authorization |
| G5 | @mentions | C | 6 | |

## EPIC H — Platform, quality and operations

| ID | Story | Pri | Phase |
|---|---|---|---|
| H1 | Docker Compose: db, migrate, 2+ app replicas, nginx | M | 2 (single replica) → 5 |
| H2 | Flyway migrations via one-shot service | M | 2 |
| H3 | OpenAPI/Swagger UI (dev profile only) | M | 2 |
| H4 | GitHub Actions: backend build+tests (Testcontainers), frontend lint+test+build, Docker image build | M | 2 → 6 |
| H5 | Structured logs + correlation IDs | M | 2 |
| H6 | Actuator health groups, Prometheus metrics | M | 2/5 |
| H7 | Nginx LB + edge limits + failover test | M | 5 |
| H8 | Shared rate limiting (Bucket4j/Postgres) | M | 3/5 |
| H9 | k6 load tests with reported results | M | 5 |
| H10 | AI evaluation dataset + runner + report | M | 3/4 |
| H11 | Playwright E2E for critical flows | M | 6 |
| H12 | Prometheus + Grafana dashboards | S | 5 |
| H13 | Modulith boundary verification test + generated module docs | M | 2 |
| H14 | Postgres Row-Level Security safety net | C | 6 |
| H15 | Demo seed data generator (deterministic, realistic project with known risks) | M | 3 |

---

## Definition of done (every story)
1. Backend authorization enforced and tested (including a cross-tenant negative test where data is tenant-owned).
2. Input validated; errors returned as RFC 9457 Problem Details.
3. Unit and/or integration tests pass in CI.
4. No secrets or sensitive content in logs.
5. API documented in OpenAPI.
6. UI handles loading, empty and error states.
