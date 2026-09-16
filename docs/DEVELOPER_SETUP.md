# Developer Setup

Onboarding guide for anyone cloning `pramaan-next`.

---

## 1. Tech stack overview

`pramaan-next` is a greenfield rebuild (the existing ECS system is kept
elsewhere as read-only reference and is never modified from this repo). It
has three runtimes plus a persistence layer, each with a distinct job:

| Path              | Stack                                | Purpose |
|-------------------|---------------------------------------|---------|
| `backend-java/`   | Java 21, Spring Boot, Maven Wrapper   | Core backend service — REST API, business logic, persistence access. |
| `agents-go/`      | Go 1.23                               | Agent / collector runtime — the processes that gather evidence from external/simulated systems. |
| `frontend-react/` | React 18 + TypeScript + Vite          | Web UI. |
| `contracts/`      | OpenAPI, protobuf                     | Shared interfaces between backend, agents, and frontend, so the three runtimes don't drift out of sync. |
| `infra/`          | SQL init scripts, service notes       | Local infra config for the Docker Compose stack. |
| `docs/`           | Markdown                              | Setup and design docs (this file included). |

Persistence, defined in the root `docker-compose.yml`:

- **PostgreSQL** — primary relational store for the backend (`postgres`
  service, host port `5433`).
- **pgvector** — a *separate* Postgres+pgvector container (`pgvector`
  service, host port `5434`) for embedding storage, used by Phase 2 AI
  features. Not the same database as the primary Postgres.
- **MinIO** — S3-compatible object storage (`minio` service, ports `9000`
  API / `9001` console) standing in for evidence-file storage.

No Python is used anywhere in the stack.

---

## 2. Prerequisites

Install these before working in this repo:

| Tool | Required version | Windows | macOS | Linux (Debian/Ubuntu) |
|------|-------------------|---------|-------|------------------------|
| Git | any recent | `winget install --id Git.Git -e --source winget` | `brew install git` | `sudo apt install git` |
| Java (JDK) | 21 | `winget install --id EclipseAdoptium.Temurin.21.JDK -e --source winget` | `brew install temurin@21` | `sudo apt install openjdk-21-jdk` |
| Go | 1.23+ | `winget install --id GoLang.Go -e --source winget` | `brew install go` | `sudo apt install golang` (or the tarball from go.dev) |
| Node.js | LTS (even-numbered: 20/22/24) + npm | `winget install --id OpenJS.NodeJS.LTS -e --source winget` | `brew install node@22` | `curl -fsSL https://deb.nodesource.com/setup_22.x \| sudo -E bash - && sudo apt install nodejs` |
| Docker | Docker Desktop (or Engine + Compose plugin on Linux) | `winget install --id Docker.DockerDesktop -e --source winget` | Docker Desktop for Mac | `sudo apt install docker.io docker-compose-plugin` |

Notes:

- **Maven** is not a separate prerequisite. `backend-java/` ships the Maven
  Wrapper (`mvnw` / `mvnw.cmd`, pinned via
  `.mvn/wrapper/maven-wrapper.properties`); it downloads its own Maven on
  first use and only needs a JDK on `PATH`. Do not install a system-wide
  Maven.
- After installing Java or Go, restart your terminal so `PATH`/`JAVA_HOME`
  pick up the change.
- Docker must be **running**, not just installed, before using
  `docker compose` or `start.sh` modes that use it.

### Verify the toolchain

```bash
git --version
java -version && javac -version   # expect 21.x
go version                        # expect 1.23+
node --version && npm --version   # expect an even-numbered LTS
docker --version && docker compose version
```

Build each service once from the repo root:

```bash
cd backend-java && ./mvnw -q compile && cd ..        # mvnw.cmd on Windows
cd agents-go && go build ./... && go test ./... && cd ..
cd frontend-react && npm install && npm run build && cd ..
```

Bring up local infra and confirm it's healthy:

```bash
cp .env.example .env
docker compose up -d
docker compose ps   # postgres, pgvector, minio should all report healthy
docker compose down # -v to also wipe data volumes
```

---

## 3. Running the app (`start.sh`)

`start.sh` is a Bash script at the repo root that starts backend + frontend
(and, in two of its three modes, the Docker storage stack) for local
development, and tears down only what it started when you press Ctrl+C. It
is written for **Git Bash (MINGW64)** on Windows; on macOS/Linux run it from
any Bash shell.

### 3.1 What it needs

- **Bash** — on Windows, Git Bash (MINGW64). The script uses `/dev/tcp/...`
  (Bash's built-in TCP check), `netstat`, and `taskkill`, all
  Windows/Git-Bash specific fallbacks for process/port management.
- **`curl`** on `PATH` — used for all HTTP health polling and the demo-seed
  POST.
- **Docker running** (not just installed) — required for modes `D` and `L`;
  the script checks `docker info` and fails fast with a clear message if it
  isn't up.
- Nothing needs to be pre-built — `start.sh` invokes `mvnw`/`mvnw.cmd` and
  `npm run dev` directly, and runs `npm install` itself if
  `frontend-react/node_modules` is missing.

### 3.2 What it does, step by step

1. **Resolve repo root** from the script's own location and `cd` into it
   (safe to invoke from anywhere).
2. **Set up cleanup.** Registers a `trap cleanup EXIT INT TERM` before doing
   anything else, so any exit path (Ctrl+C, error, normal exit) stops only
   the processes/containers this run started.
3. **Show the menu** and read one key:
   - `D` — **Demo**: `docker compose up -d` for *all* compose services
     (`postgres`, `pgvector`, `minio`), wait for the `pramaan-postgres`
     container's Docker healthcheck, then start the app layer on the
     **default** Spring profile with `BACKEND_EXTRA_JVM_ARGS` forcing
     `-Duser.timezone=Asia/Kolkata` (Windows timezone workaround, see
     CLAUDE.md decision log) plus `-Dpramaan.object-store.driver=minio
     -Dpramaan.ai.vector-store=pgvector` — the only mode where the backend
     actually points at MinIO/pgvector instead of the filesystem/in-memory
     defaults.
   - `L` — **Low mem**: `docker compose up -d` for only
     `postgres pgvector minio` (the `STORAGE_SERVICES` array), wait for a
     raw TCP connect on `localhost:5433` (the host-mapped Postgres port),
     then start the app layer on the **default** profile. Object
     store/vector store stay on their `application.yml` defaults
     (filesystem/memory) — no extra JVM args are set here.
   - `R` — **Normal**: no Docker at all. Starts the app layer on the `dev`
     Spring profile (H2 in-memory DB, filesystem object store — see
     `backend-java/src/main/resources/application-dev.yml`).
   - `Q` / empty — exit immediately (disarms the trap first, since nothing
     was started).
4. **Resolve demo mode** (`resolve_demo_mode`, run for `D`/`L`/`R` before
   dispatch): if the `DEMO_MODE` env var is already set it's normalized to
   `true`/`false` and used as-is; otherwise demo mode defaults **on**
   (`DEMO_MODE=true`) with no interactive prompt, and is exported so the
   backend JVM inherits it.
5. **Start the app layer** (`start_app_layer`, shared by all three modes):
   1. `require_port_free 8080` — hard-fails if anything is already
      listening on the backend port, to avoid silently talking to a
      leftover orphan process from a previous run (see the long comment in
      `require_port_free` in start.sh).
   2. **Start backend**: always with Spring profile `local` added (a no-op
      unless a gitignored `backend-java/config/application-local.yml`
      exists), plus `dev` for mode `R`. Output redirected to
      `backend.log`. If demo mode is on, `-DDEMO_MODE=true` is passed as a
      JVM system property (in addition to the exported env var, since the
      `mvnw.cmd` → `cmd.exe` hop on Windows can drop env vars).
   3. **Wait** for `GET http://localhost:8080/actuator/health` to respond
      (up to 180s) — exits the whole script on timeout.
   4. **If demo mode is on**, `POST /api/v1/dev/seed-demo-evidence` to seed
      demo evidence records (12 records across 3 demo apps/10 frameworks —
      see `DemoSeedController`). A failure here is logged as a warning but
      does **not** abort the script.
   5. `require_port_free 5173`, then **start frontend** (`npm run dev`,
      running `npm install` first if `node_modules` is missing). Output
      redirected to `frontend.log`.
   6. **Wait** for `GET http://localhost:5173` to respond (up to 120s) —
      exits on timeout.
   7. Print a status banner (URLs, log paths, Docker services, demo status)
      and enter a **liveness-monitor loop**: every 5s, poll both health
      URLs; a service is only declared "down" (triggering shutdown) after
      3 consecutive failed polls (`MONITOR_GRACE_FAILS`), so one slow
      response doesn't cause a false shutdown.
6. **On exit** (Ctrl+C or a monitored service going down), the `cleanup`
   trap fires: stop frontend, then backend (by captured PID and by
   whatever is actually listening on the port — see 3.5), then
   `docker compose stop` (not `down`) for whichever services this run
   started. Containers are stopped, not removed; data volumes are kept.

### 3.3 How to run it

From the repo root, in Git Bash (MINGW64) on Windows, or any Bash shell on
macOS/Linux:

```bash
./start.sh
```

Then type `D`, `L`, or `R` at the prompt and press Enter.

To skip the interactive demo-mode question, set the env var first:

```bash
DEMO_MODE=false ./start.sh
```

Stop everything with **Ctrl+C** in the same terminal — do not close the
terminal window directly, or the EXIT trap won't run (see 3.4).

### 3.4 Health checks

| Component | How to confirm it's up | Notes |
|-----------|------------------------|-------|
| Backend   | `curl http://localhost:8080/actuator/health` → `{"status":"UP"}` | Only `health` and `info` actuator endpoints are exposed (`application.yml`). Script polls this for up to 180s. |
| Frontend  | Open http://localhost:5173, or `curl -sf http://localhost:5173` | Script polls for up to 120s. Vite dev server; first compile can be slow. |
| Postgres (mode D) | `docker inspect --format='{{.State.Health.Status}}' pramaan-postgres` → `healthy` | Script waits up to 120s on the container healthcheck (`pg_isready`). |
| Postgres (mode L) | Script waits for a raw TCP accept on `localhost:5433`, up to 120s | This is weaker than mode D's check — a TCP accept doesn't mean Postgres has finished initializing, only that *something* is listening. |
| pgvector / MinIO | Not polled by `start.sh` at all in any mode | Containers are started (`up -d`) but the script never waits on their healthchecks or ports directly. Check manually: `docker compose ps` should show `healthy` for `pgvector`/`minio`. |
| Demo seed (if demo mode on) | Console line `demo-evidence seed OK — seeded=12 created=... duplicates=...` | Failure prints a `WARNING` with a retry `curl` command but does not stop the script. |
| Logs | `backend.log`, `frontend.log` in repo root | Truncated (`: >`) at the start of each run before the process is launched. |

Startup time: backend health-poll timeout is 180s (generous — first Maven
run may need to resolve dependencies via the wrapper), frontend/Postgres
timeouts are 120s. Nothing in the script is flagged as *expected* to take
that long on a warm cache; the long timeouts are headroom, not a hint that
it normally takes minutes.

### 3.5 Common failure modes (from what the script itself guards against)

- **"something is already listening on :8080 / :5173"** — `require_port_free`
  refuses to start on top of an existing listener, because a previous run's
  real process (see next point) may be a silently-stale orphan answering
  health checks while serving old code. Fix: run the `taskkill //PID <pid>
  //T //F` command the error prints (or `netstat -ano | grep ":<port>.*LISTENING"`
  to inspect first), then re-run `./start.sh`.
- **Orphaned backend/frontend process after a terminal was closed instead of
  Ctrl+C'd.** On MINGW64, `$!` captured right after launching `mvnw.cmd` /
  `npm` is the shim's PID, not the real `java.exe`/`node.exe` PID — closing
  the terminal (instead of Ctrl+C, which runs the EXIT trap) can leave the
  real process running and bound to the port. This is exactly what
  `require_port_free` is designed to catch on the next run.
- **Docker not running** (modes `D`/`L`) — `require_docker` checks
  `docker info` and exits with `"Docker Desktop isn't running — start it or
  choose R (Normal, no Docker)."` before attempting anything else.
- **`docker compose up` fails** (e.g. port already bound by something else,
  bad image pull) — script exits immediately with `"docker compose up
  failed."`; check `docker compose logs`.
- **`pramaan-postgres` container never reports healthy** (mode `D`) — exits
  after 120s with the last known status; check `docker compose logs postgres`.
- **Backend never answers `/actuator/health` within 180s** — script exits;
  check `backend.log` for a genuine startup failure (e.g. Flyway migration
  error, missing DB) as opposed to it just being slow.
- **Frontend never answers within 120s** — script exits; check `frontend.log`
  (e.g. `npm install` failure, Vite port conflict despite the earlier
  `require_port_free` check).
- **Demo-evidence seed POST fails** — logged as a warning
  (`backend unreachable or endpoint returned an error`) and the script
  continues; retry manually with the printed `curl -X POST
  http://localhost:8080/api/v1/dev/seed-demo-evidence` once the backend is
  confirmed up. Note the seed endpoint only exists at all when
  `pramaan.demo-mode=true` (`DemoSeedController` is
  `@ConditionalOnProperty`-gated) — with demo mode off, that path 404s.
- **Windows-timezone Postgres startup failure** (mode `D` specifically, on
  Windows hosts) — already worked around in the script itself
  (`-Duser.timezone=Asia/Kolkata` JVM arg); see the CLAUDE.md decision log
  if it resurfaces.

---

## 4. Stopping / restarting cleanly

- **Stop**: press **Ctrl+C** in the terminal running `start.sh`. This fires
  the `cleanup` trap, which stops the frontend, then the backend (by PID and
  by port), then `docker compose stop` for any containers this run started
  (data volumes are kept — use `docker compose down -v` separately to wipe
  them).
- **Do not** close the terminal window/tab directly to stop it — that skips
  the EXIT trap and can leave orphaned backend/frontend processes bound to
  their ports (see §3.5). If that happens, use the `taskkill` command from
  the resulting `require_port_free` error on the next run.
- **After backend code changes, a full kill-and-restart of `start.sh` is
  required.** The backend runs via `mvnw spring-boot:run` with no
  file-watcher/reload wired up in this script (`spring-boot-devtools` is not
  invoked), so edits to backend-java source are **not** picked up live —
  Ctrl+C, then `./start.sh` again. (Vite's frontend dev server does hot-reload
  frontend-react changes on its own; that part doesn't need a restart.)
- **Restart** is just: Ctrl+C, wait for `"Done."`, then `./start.sh` again
  and re-pick a mode. `require_port_free` will catch it if the previous
  run didn't fully release its ports yet.

---

## 5. Notes / open questions

- **`PRAMAAN_START_DRYRUN=1`** is a documented escape hatch ("used by the
  focused test") that makes every mode print the commands it would run and
  exit without starting anything — useful for exercising the script's logic
  without Docker/Java/Node. As of this read, no test file in the repo
  actually invokes it (a `grep` for `PRAMAAN_START_DRYRUN` outside
  `start.sh` turns up nothing) — the referenced "focused test" appears to
  not exist yet, or lives outside this checkout.
- **Redis is mentioned but not present.** The menu text for mode `L` says
  "Postgres, pgvector, MinIO, Redis if present," and the `STORAGE_SERVICES`
  comment notes "(No redis in this compose file.)" — `docker-compose.yml`
  confirms there is no `redis` service. The menu line is stale/aspirational
  text, not a bug in the logic.
- **`backend-java/config/application-local.yml` is a convention, not
  something in this repo.** The `local` Spring profile is always added so a
  developer's own gitignored override file (if they create one) is picked
  up; no such file exists by default, and this is a no-op for a fresh
  clone.
- **pgvector/MinIO health is unchecked** even though they're started in
  modes `D`/`L` (see §3.4) — only Postgres has a wait step. Not something
  the script itself flags as a TODO, but worth knowing before assuming
  those services are ready right when the banner prints.
- **`.env` is not read by `start.sh` itself.** `docker-compose.yml`'s
  `POSTGRES_USER`/`PASSWORD`/`DB` etc. have hardcoded values inline (not
  `${VAR}` substitutions), so `.env.example` → `.env` isn't actually
  required for `docker compose up` to work with the values
  start.sh/application.yml expect — the `.env.example` values just happen
  to match the hardcoded compose defaults.
