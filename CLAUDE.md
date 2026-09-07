# CLAUDE.md — Pramaan Next

Guidance for AI assistants and contributors working in this repository.

## What this repo is

`pramaan-next` is a greenfield rebuild. It is a **separate Git repository** from the
existing ECS codebase.

## Hard rules

1. **The ECS source is READ-ONLY reference.** It lives in a different repository
   (`../ecs-enterprise-backup`). Never modify, move, or delete anything there.
   Read it for reference only.
2. **No Python.** The stack is Java, Go, and TypeScript only.
3. **No business features yet.** The repo is scaffolding. Do not add domain
   entities, controllers, endpoints, agent logic, or UI screens until the
   corresponding contract exists in `contracts/` and the work is asked for.
4. **Do not push.** Commit locally; pushing happens only after review.
5. Contracts first: define an interface in `contracts/` before implementing the
   services on either side of it.

## Layout

| Path             | Stack                              | Purpose                          |
|------------------|------------------------------------|----------------------------------|
| `backend-java/`  | Java 21, Spring Boot, Maven Wrapper| Core backend service             |
| `agents-go/`     | Go 1.23                            | Agent runtime                    |
| `frontend-react/`| React 18 + TypeScript + Vite       | Web UI                           |
| `contracts/`     | OpenAPI, protobuf                  | Shared cross-service interfaces  |
| `infra/`         | SQL init, service notes            | Local infra config               |
| `docs/`          | Markdown                           | Setup and design docs            |

Root `docker-compose.yml` runs PostgreSQL, pgvector, and MinIO for local dev.

## Common commands

```bash
# infra
docker compose up -d
docker compose down          # add -v to wipe data volumes

# backend
cd backend-java && ./mvnw verify        # use mvnw.cmd on Windows

# agents
cd agents-go && go build ./... && go test ./...

# frontend
cd frontend-react && npm install && npm run build
```

See `docs/DEVELOPER_SETUP.md` for tool installation and verification.
