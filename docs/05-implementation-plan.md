# 05 — Phased Implementation Plan

Each phase ends with **exit criteria that are verified by actually running something**. Nothing is reported as done or tested unless it was executed.

## Prerequisites (local machine)
- **JDK 21.** The machine currently has JDK 17, which Spring Boot 4 + this project's `release 21` will not compile. Alternative: build only inside Docker.
- Docker Desktop (installed: 29.4), Node 24 (installed), Git (installed).
- Maven is **not** needed. The project ships the Maven Wrapper.
- **Ollama** (free, local) is needed from Phase 3: install it from ollama.com, then run `ollama pull qwen3:4b` and `ollama pull nomic-embed-text`. No API keys are needed. Tests never call a real model; they use a fake `ChatModel`/`EmbeddingModel`.

---

## Phase 1 — Planning and architecture ✅ (this delivery)
Deliverables: architecture and trade-offs, backlog, data model / ER, repository structure, API preview, this plan.
**Exit:** documents reviewed. Open decisions (below) answered.

## Phase 2 — Application foundation  *(largest phase; split into 4 increments)*

| Inc. | Scope | Verified by |
|---|---|---|
| 2.1 | Repo skeleton: Maven wrapper, Boot 4.1 app, `shared` (ProblemDetails, RequestId, Clock), Postgres + `migrate` service in Compose, Modulith verify test, Testcontainers base, Vite React app shell, CI workflow | `./mvnw verify` green; `docker compose up` → `/actuator/health` UP; CI green |
| 2.2 | `identity`: register/login/refresh/logout, JWT filter, Argon2, refresh rotation + reuse detection, login rate limit, audit events. Frontend auth pages + silent refresh | Auth integration tests incl. reuse-detection and CSRF-header tests |
| 2.3 | `organizations` + `projects`: orgs, memberships, invitations, roles, `ProjectAccess`/`ProjectScope`, projects, members, milestones. Frontend org switcher, project list/detail | **Cross-tenant test suite** (user of org B gets 404 on every org-A endpoint) |
| 2.4 | `tasks` + `collaboration` + `notifications` (in-app, no WS yet) + `audit` query: tasks, dependencies with cycle check, blocked/overdue, comments, activity feed, optimistic locking. Frontend task list, board, task detail | Concurrency test (two parallel updates → one 409); N+1 guard test; dependency-cycle tests |

## Phase 3 — Analytics + AI Project Analyst
1. `analytics`: deterministic metrics, health score, snapshots (ShedLock), dashboard with Recharts.
2. Deterministic **demo seed scenario** with planted risks: an overdue critical-path task, a blocked milestone, an overloaded member. It doubles as the evaluation ground truth.
3. Spring AI integration behind an `AiGateway` (timeouts, bulkhead, retry, circuit breaker, metrics).
4. Six read-only tools, `EvidenceLedger`, `AnalystResponse` validation, conversation persistence.
5. Analyst chat UI with an evidence panel and clickable references.
6. Evaluation dataset v1 (~40 questions) + runner + first results.

**Exit:** tool-authorization tests (tool cannot reach another project, revoked member denied); invalid-LLM-output tests; timeout/429/circuit-open tests using a fake model + WireMock; eval report generated from a real run.

## Phase 4 — Knowledge Hub (RAG)
Upload validation → storage abstraction → DB job queue (`SKIP LOCKED`) → PDFBox extraction → chunking → embeddings → pgvector (HNSW, iterative scan) → grounded answer with page citations → deletion → analyst tool `searchProjectDocuments`. Eval set extended with document questions and **prompt-injection PDFs**.

**Exit:** retrieval tests on fixture PDFs (expected chunk in top-k); cross-tenant retrieval test; injection eval cases pass; ingestion survives killing the replica mid-job (job reclaimed after lock timeout).

## Phase 5 — Traffic management and real-time
Nginx config (least_conn, passive health, idempotent-only retry, limits, timeouts, WS upgrade), 2–3 replicas, readiness-driven drain, Bucket4j shared limits, WebSocket fan-out via `LISTEN/NOTIFY`, Prometheus/Grafana. k6 scenarios:
- **distribution:** requests spread across replicas (each replica returns its instance id in a header).
- **failover:** stop one replica during the run, then measure error rate and recovery time.
- **rate-limit:** show that a limit holds globally across replicas.
- **ai-burst:** concurrent AI requests exceed the bulkhead and get fast 503s, while normal endpoints keep their latency SLO.
- **crud-mix:** baseline throughput and p95, with the bottleneck identified (DB pool? CPU?) *before* changing any limit.

**Exit:** measured results committed to `loadtest/results/` with environment details and limitations.

## Phase 6 — Final delivery
Playwright E2E (register → org → project → tasks → AI question → document Q&A), CI completion (image build, E2E against Compose), optional RLS, README, the remaining ADRs, the academic report, and a demo script with a fallback recording.

---

## Testing strategy overview

| Layer | Tooling | Examples |
|---|---|---|
| Unit | JUnit 5, Mockito, AssertJ | Health score table tests, cycle detection, permission matrix, evidence ledger |
| Module integration | `@ApplicationModuleTest` (Modulith) + Testcontainers Postgres | Task service with real DB, events published |
| API integration | MockMvc/`RestTestClient` + Testcontainers | Auth flows, 404 cross-tenant, 409 conflicts, Problem Details shape |
| AI | Fake `ChatModel` scripted per test; WireMock for HTTP-level provider faults | Malformed JSON, fabricated refs, tool-call to forbidden scope, timeout, 429 |
| Retrieval | Testcontainers pgvector + deterministic fake embeddings (and a tagged real-embedding test) | Top-k contains expected chunk; tenant filter |
| Architecture | Modulith `verify()`, ArchUnit | No cross-module internals; no `findById` on tenant repos |
| Frontend | Vitest, Testing Library, MSW | Token refresh, evidence panel rendering, error states |
| E2E | Playwright against Compose | Critical flows |
| Load | k6 | See Phase 5 |
| AI evaluation | Custom runner (tagged, not in default CI) | Metrics below |

### AI evaluation metrics
| Metric | How it's measured |
|---|---|
| Factual correctness | Each question has expected facts (e.g. "overdue set = {FP-12, FP-17}"). They are checked programmatically against the structured answer's refs, with a rubric for free text. |
| Citation correctness | Cited refs ⊆ expected-supporting refs; precision/recall |
| Unsupported-claim rate | Fraction of answers with refs ∉ ledger (before stripping) + rubric-judged unsupported statements |
| Retrieval relevance | Recall@k and MRR for document questions with labelled gold chunks |
| Cross-tenant violations | Count of any ref/chunk from another org in tool results or answers (target: 0, hard-fail) |
| Latency | p50/p95 end-to-end and LLM-only |
| Cost | Token usage × configured price table → per-answer and per-run estimate |

LLM output is non-deterministic, so runs repeat each question N times (e.g. 3) and report the mean and spread.

---

## Decisions
| Decision | Outcome |
|---|---|
| AI provider | **Ollama, local and free** (`qwen3:4b` chat, `nomic-embed-text` embeddings). Hosted providers stay possible through configuration. Decided 2026-10-15. |
| JDK | Build inside Docker until JDK 21 is installed locally (default; not yet confirmed) |
| Git | Local repository; GitHub remote to be added for Actions (default; not yet confirmed) |
| UI language | English only (default; not yet confirmed) |
