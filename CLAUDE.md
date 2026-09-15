# CLAUDE.md — Pramaan Next

Guidance for AI assistants and contributors. Keep responses concise.

## Stack

- **Java 21 + Spring Boot** — backend / core services (`backend-java/`)
- **Go** — agents / technical collectors (`agents-go/`)
- **React + TypeScript** — frontend (`frontend-react/`)
- **PostgreSQL / pgvector / object storage (MinIO)** — persistence (`infra/`, root `docker-compose.yml`)
- **No Python** anywhere in the stack.

## Isolation

- The existing ECS codebase is **read-only reference** (separate repo, `../ecs-enterprise-backup`).
- **Never modify any file outside `pramaan-next/`.**
- **Never import or copy runtime code from the old ECS.** Reference it for behavior and contracts only; reimplement in this stack.

## Team ownership

| Path              | Owner      |
|-------------------|------------|
| `backend-java/`   | Teammate 1 |
| `contracts/`      | Teammate 1 |
| `infra/`          | Teammate 1 |
| `agents-go/`      | Teammate 2 |
| `frontend-react/` | Teammate 3 |

Coordinate before changing files outside your area.

## Rules

- **Reuse before creating.** Check for an existing endpoint/service/contract before adding one.
- **No duplicate APIs or services.**
- **Mock or simulate** external systems that are unavailable locally.
- Run **focused tests only** (the tests relevant to the change), not full suites.
- **Never commit or push unless explicitly requested.**
- Keep Claude responses concise.

## Layout

| Path             | Stack                               | Purpose                         |
|------------------|-------------------------------------|---------------------------------|
| `backend-java/`  | Java 21, Spring Boot, Maven Wrapper | Core backend service            |
| `agents-go/`     | Go 1.23                             | Agent / collector runtime       |
| `frontend-react/`| React 18 + TypeScript + Vite        | Web UI                          |
| `contracts/`     | OpenAPI, protobuf                   | Shared cross-service interfaces |
| `infra/`         | SQL init, service notes             | Local infra config              |
| `docs/`          | Markdown                            | Setup and design docs           |

Root `docker-compose.yml` runs PostgreSQL, pgvector, and MinIO for local dev.

## Common commands

```bash
# infra
docker compose up -d
docker compose down          # add -v to wipe data volumes

# backend
cd backend-java && ./mvnw verify        # mvnw.cmd on Windows

# agents
cd agents-go && go build ./... && go test ./...

# frontend
cd frontend-react && npm install && npm run build
```

See `docs/DEVELOPER_SETUP.md` for tool installation and verification.

## Decision log

- **2026-09-15** — `pgvector` and `minio` containers start under `start.sh`
  option D (and option L) but are not currently wired into `backend-java`
  (`pramaan.ai.vector-store` defaults to memory / object store defaults to
  filesystem). This is intentional, not a bug — do not "fix" by adding
  pgvector/MinIO client wiring without a separate discussion.
- **2026-09-15** — Docker Postgres (option D, `run_demo`) fails to start with
  `FATAL: invalid value for parameter "TimeZone": "Asia/Calcutta"` on Windows
  hosts whose OS zone is "India Standard Time": pgjdbc sends this as a
  top-level startup-packet parameter (computed from the JVM's default
  `TimeZone`, independent of any `?options=...` JDBC URL override), and
  Postgres 16 dropped that legacy tz-database alias. Fixed by forcing
  `-Duser.timezone=Asia/Kolkata` as a JVM arg on the option-D backend launch
  only (`start.sh`'s `run_demo`, via `BACKEND_EXTRA_JVM_ARGS`). A `TZ`/`PGTZ`
  env var on the `postgres` service would NOT fix this — it only changes the
  container's own default zone, not the value the client sends, which is
  what Postgres validates and rejects.
