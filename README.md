# Pramaan Next

Greenfield rebuild. Separate repository from the existing ECS system, which is
kept only as **read-only reference** and must never be modified.

## Structure

```
pramaan-next/
├─ backend-java/    Java 21 · Spring Boot · Maven Wrapper
├─ agents-go/       Go 1.23
├─ frontend-react/  React 18 · TypeScript · Vite
├─ contracts/       OpenAPI + protobuf (shared interfaces)
├─ infra/           Local infra config + DB init scripts
├─ docs/            Setup and design docs
└─ docker-compose.yml   PostgreSQL · pgvector · MinIO
```

## Status

Scaffolding only. No business features. No Python anywhere in the stack.

## Getting started

1. Install and verify the toolchain — see [docs/DEVELOPER_SETUP.md](docs/DEVELOPER_SETUP.md).
2. `cp .env.example .env`
3. Build each service once (see [CLAUDE.md](CLAUDE.md) → Common commands).

### Running locally

Use the startup helper at the repo root instead of juggling terminals:

```bash
./start.sh          # macOS/Linux/Git Bash
start.cmd           # Windows (double-click or run from cmd/PowerShell)
```

It shows a menu:

| Mode         | What it starts |
|--------------|----------------|
| **D** Demo   | Full `docker compose` stack + backend (Docker-infra profile) + frontend |
| **L** Low mem| Storage containers only (`postgres`, `pgvector`, `minio`) + backend + frontend |
| **R** Normal | No Docker — backend on the `dev` profile (H2 + filesystem) + frontend |
| **Q** Quit   | Exit, no side effects |

Backend runs on <http://localhost:8080>, frontend on <http://localhost:5173>.
Output goes to `backend.log` / `frontend.log` in the repo root. Ctrl+C stops
only what the script started. `PRAMAAN_START_DRYRUN=1 ./start.sh` prints the
commands for a mode without running them.
