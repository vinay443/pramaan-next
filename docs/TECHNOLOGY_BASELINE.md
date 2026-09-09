# Technology Baseline — Pramaan Next

Authoritative toolchain and dependency versions for the rebuild. Captured by
direct inspection on 2026-09-08. Update this file when a baseline version changes.

## Toolchain

| Tool | Baseline (target) | Installed on dev machine | Pinned by | Status |
|------|-------------------|--------------------------|-----------|--------|
| Java / JDK | 21 LTS | Temurin 21.0.12.1+1 LTS | `backend-java/pom.xml` `<java.version>21</java.version>` | PASS |
| Spring Boot | 3.3.5 | — | `pom.xml` parent | PASS (single version, Java 21 compatible) |
| Maven | 3.9.9 (via wrapper) | wrapper-provided | `.mvn/wrapper/maven-wrapper.properties` (wrapper 3.3.2, `only-script`) | PASS |
| Go | 1.23 | go1.27.0 | `agents-go/go.mod` `go 1.23` | PASS (no `toolchain` directive — see gaps) |
| Node.js | LTS (v20/v22/v24) | v24.19.0 | not pinned (`package.json` has no `engines`, no `.nvmrc`) | PARTIAL — see gaps |
| npm | 10+/11 | 11.17.0 | lockfile `frontend-react/package-lock.json` (present, untracked) | PASS (lockfile present; commit it) |
| TypeScript | 5.6.x | 5.6.3 (`typescript ^5.6.3`) | `frontend-react/package.json` | PASS |
| React | 18.3.x | 18.3.1 (`react ^18.3.1`) | `package.json` | PASS |
| Vite | 5.4.x | 5.4.21 | `package.json` | PASS |
| Docker Engine | 24+ | 29.7.2 | host | PASS |

## Docker images (`docker-compose.yml`)

| Service | Image | Pinned | Status |
|---------|-------|--------|--------|
| postgres | `postgres:16` | major tag only | PARTIAL (acceptable; no digest) |
| pgvector | `pgvector/pgvector:pg16` | major tag | PASS |
| minio | `minio/minio:latest` | **not pinned** | FAIL — see gaps |

Ports: postgres `5433:5432`, pgvector `5434:5432`, minio `9000/9001`.

## Backend dependency set (`backend-java/pom.xml`)

Spring Boot BOM-managed (no explicit versions): `spring-boot-starter-web`,
`-data-jpa`, `-validation`, `-actuator`, `-test`. Plus `flyway-core` +
`flyway-database-postgresql`, `postgresql` (runtime), `h2` (runtime/optional —
tests + `dev` profile). Migrations: `src/main/resources/db/migration/V1..V3`.

## Frontend dependency set

Runtime: `react`, `react-dom`, `react-router-dom` ^6.26. Dev/test:
`vitest` ^2.1, `@testing-library/*`, `jsdom`, `@vitejs/plugin-react`.
Build = `tsc --noEmit` + `vite build`.

## Go module

`github.com/pramaan/pramaan-next/agents-go`, `go 1.23`. No third-party
dependencies (stdlib only).

## Focused verification (2026-09-08)

| Component | Command | Result |
|-----------|---------|--------|
| backend | `./mvnw test` | PASS — 35 tests, 0 failures, BUILD SUCCESS |
| agents | `go build ./... && go test ./...` | PASS — all packages ok |
| frontend build | `npm run build` | PASS — `tsc --noEmit` clean, `dist/` produced |
| frontend tests | `npm test` | PASS — 11 files, 30 tests |

## Known baseline gaps (non-blocking)

1. `minio/minio:latest` → pin to a dated release tag (e.g. `RELEASE.2025-xx-xxTxx-xx-xxZ`).
2. Node not pinned — add `frontend-react/.nvmrc` (`22` or `24`) and an
   `"engines": { "node": ">=22" }` block; commit `package-lock.json`.
3. `agents-go/go.mod` — consider adding `toolchain go1.23.x` so all builds use
   one Go version regardless of the installed SDK.
4. `postgres:16` / `pgvector:pg16` — optionally pin to minor tags or digests.
