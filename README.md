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
3. `docker compose up -d`
4. Build each service (see [CLAUDE.md](CLAUDE.md) → Common commands).
