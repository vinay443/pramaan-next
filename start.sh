#!/usr/bin/env bash
#
# Pramaan Next — local startup helper.
# Bash / Git Bash (MINGW64) compatible. Run from anywhere:
#     ./start.sh
#
# Starts the backend (:8080) and frontend (:5173) in the background with output
# redirected to backend.log / frontend.log in the repo root. On exit it stops
# only the processes and containers THIS script started.

set -u

# --- resolve repo root (this script's directory) ---------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

BACKEND_DIR="$SCRIPT_DIR/backend-java"
FRONTEND_DIR="$SCRIPT_DIR/frontend-react"
BACKEND_LOG="$SCRIPT_DIR/backend.log"
FRONTEND_LOG="$SCRIPT_DIR/frontend.log"

COMPOSE_PG_HOST_PORT=5433          # docker-compose.yml: postgres  ports 5433:5432
BACKEND_PORT=8080
FRONTEND_PORT=5173
BACKEND_URL="http://localhost:$BACKEND_PORT"
FRONTEND_URL="http://localhost:$FRONTEND_PORT"

# Liveness monitor tuning. The captured $! is unreliable on MINGW64 (mvnw.cmd /
# npm launch the real JVM/Node through a shim that exits after hand-off), so
# after startup we watch the *ports*, not the PID. Only shut down after this
# many consecutive failed polls, to ride out a single slow response.
MONITOR_INTERVAL=5
MONITOR_GRACE_FAILS=3

# Storage service names exactly as defined in docker-compose.yml.
# (No redis in this compose file.)
STORAGE_SERVICES=(postgres pgvector minio)

# Set PRAMAAN_START_DRYRUN=1 to print the commands each mode would run and exit
# without starting anything (used by the focused test).
DRYRUN="${PRAMAAN_START_DRYRUN:-}"

# --- state: what we started (for cleanup) ---------------------------------
BACKEND_PID=""
FRONTEND_PID=""
STARTED_COMPOSE=""        # non-empty => we ran `docker compose up`
COMPOSE_SERVICES=()       # services we brought up

# --- demo mode ----------------------------------------------------------
# Resolved once, up front (env var or interactive prompt), then exported so the
# backend JVM inherits it. Never left implicit.
DEMO_MODE_ON="no"          # "yes" | "no" — for terminal output
DEMO_SEED_STATUS=""        # "" | "ok" | "failed"
DEMO_SEED_DETAIL=""        # human-readable seed summary

# --- helpers -------------------------------------------------------------
have() { command -v "$1" >/dev/null 2>&1; }

compose() {
  # Prefer `docker compose`, fall back to `docker-compose`.
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
  else
    docker-compose "$@"
  fi
}

docker_running() {
  have docker && docker info >/dev/null 2>&1
}

port_open() {
  # $1 host, $2 port — returns 0 if a TCP connect succeeds
  (exec 3<>"/dev/tcp/$1/$2") >/dev/null 2>&1 && { exec 3>&- 3<&-; return 0; }
  return 1
}

http_ok() {
  # $1 url — returns 0 if it answers (any HTTP status) within a short timeout
  curl -s -o /dev/null --max-time 4 "$1" 2>/dev/null
}

pids_on_port() {
  # Print the PID(s) LISTENING on the given local port. Windows `netstat`
  # (what Git Bash sees) columns: Proto  Local  Foreign  State  PID.
  local port="$1"
  have netstat || return 0
  netstat -ano 2>/dev/null \
    | grep -E "[:.]${port}[[:space:]]+[^[:space:]]+[[:space:]]+LISTENING[[:space:]]" \
    | awk '{print $NF}' \
    | grep -E '^[0-9]+$' \
    | sort -u
}

kill_pid() {
  # Force-kill one PID and its children. Git Bash process trees are not native
  # Linux, so prefer Windows taskkill //T.
  local pid="$1"
  [[ -z "$pid" ]] && return 0
  if have taskkill; then
    taskkill //PID "$pid" //T //F >/dev/null 2>&1 && return 0
  fi
  kill "$pid" 2>/dev/null
  sleep 1
  kill -9 "$pid" 2>/dev/null || true
}

require_port_free() {
  # $1 label, $2 port — hard-fail if something is already listening there.
  #
  # Root-cause guard: on MINGW64, `$!` after `mvnw.cmd`/`npm` is the shim PID,
  # not the real java.exe/node PID (see MONITOR_* note above). If a previous
  # run's terminal was closed (or killed) without going through this script's
  # own EXIT trap, that real process can survive as an orphan still bound to
  # this port. The next "restart" then either fails to bind the port (and
  # exits) or never gets the chance to serve anything — but wait_for_http only
  # polls the URL, so it happily reports success once it sees the ORPHAN
  # answering health checks. The user believes the restart worked; requests
  # keep hitting old code (existing routes work, newly added ones 404) with no
  # visible error. Failing fast here, before launching anything, turns that
  # silent-stale-process failure mode into a loud one.
  local label="$1" port="$2" existing pid kill_lines=""
  existing="$(pids_on_port "$port")"
  [[ -z "${existing// /}" ]] && return 0
  for pid in $existing; do
    kill_lines="${kill_lines}    taskkill //PID $pid //T //F"$'\n'
  done
  cat >&2 <<EOF

ERROR: something is already listening on :$port (pid(s): $(echo "$existing" | tr '\n' ' ')) —
refusing to start $label on top of it.

This is almost always a leftover process from a previous run that didn't shut
down cleanly (a closed terminal, a killed shim, etc. — the real process
survives even though it looks stopped). Starting a new $label now would either
fail to bind the port, or leave you unknowingly talking to the OLD process:
old routes keep responding while anything added since it started 404s.

Kill it, then re-run this script:
${kill_lines}(or, to double check first: netstat -ano | grep ":$port.*LISTENING")
EOF
  exit 1
}

stop_service() {
  # $1 label, $2 captured-pid (may be a stale shim), $3 port
  local label="$1" pid="$2" port="$3" killed=""
  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
    echo "  $label (pid $pid)"
    kill_pid "$pid"
    killed=1
  fi
  # The captured $! is often the mvnw/npm shim, already gone — find and kill
  # whatever is actually listening on the port.
  local real
  for real in $(pids_on_port "$port"); do
    echo "  $label — killing pid $real listening on :$port"
    kill_pid "$real"
    killed=1
  done
  [[ -z "$killed" ]] && echo "  $label — nothing to stop on :$port"
}

wait_for_port() {
  local host="$1" port="$2" name="$3" timeout="${4:-90}" waited=0
  echo "Waiting for $name ($host:$port, up to ${timeout}s)..."
  while (( waited < timeout )); do
    if port_open "$host" "$port"; then
      echo "  $name is accepting connections."
      return 0
    fi
    sleep 2; waited=$((waited + 2))
  done
  echo "  ERROR: $name did not come up within ${timeout}s." >&2
  return 1
}

wait_for_container_healthy() {
  local container="$1" name="$2" timeout="${3:-120}" waited=0
  echo "Waiting for $name container '$container' to become healthy (up to ${timeout}s)..."
  while (( waited < timeout )); do
    local status
    status=$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null)
    if [[ "$status" == "healthy" ]]; then
      echo "  $name is healthy."
      return 0
    fi
    sleep 2; waited=$((waited + 2))
  done
  echo "  ERROR: $name did not become healthy within ${timeout}s (last status: ${status:-unknown})." >&2
  return 1
}

wait_for_http() {
  local url="$1" name="$2" timeout="${3:-120}" waited=0
  echo "Waiting for $name ($url, up to ${timeout}s)..."
  # Note: we do NOT bail on "$! is dead" here — on MINGW64 the captured PID is
  # frequently the mvnw/npm shim, which exits well before the real server binds
  # the port. Rely on the timeout instead; the log shows a genuine crash.
  while (( waited < timeout )); do
    if curl -sf -o /dev/null "$url" 2>/dev/null; then
      echo "  $name is up."
      return 0
    fi
    sleep 2; waited=$((waited + 2))
  done
  echo "  ERROR: $name did not answer within ${timeout}s (still starting? check the log)." >&2
  return 1
}

# --- cleanup: only touch what we started ---------------------------------
CLEANED=""
cleanup() {
  [[ -n "$CLEANED" ]] && return 0
  CLEANED=1
  [[ -n "$DRYRUN" ]] && return 0
  [[ -z "$BACKEND_PID$FRONTEND_PID$STARTED_COMPOSE" ]] && return 0
  echo ""
  echo "Stopping services this script started..."

  [[ -n "$FRONTEND_PID" ]] && stop_service frontend "$FRONTEND_PID" "$FRONTEND_PORT"
  [[ -n "$BACKEND_PID"  ]] && stop_service backend  "$BACKEND_PID"  "$BACKEND_PORT"

  if [[ -n "$STARTED_COMPOSE" ]]; then
    echo "  docker compose stop: ${COMPOSE_SERVICES[*]}"
    if ! compose stop "${COMPOSE_SERVICES[@]}" >/dev/null 2>&1; then
      cat <<EOF
  WARNING: 'docker compose stop' did not complete cleanly. Stop manually with:
      cd "$SCRIPT_DIR" && docker compose stop ${COMPOSE_SERVICES[*]}
EOF
    fi
    echo "  (containers stopped, not removed — data volumes kept. 'docker compose down' to remove.)"
  fi

  # If anything is still listening on our ports, tell the user how to finish.
  warn_leftover() {
    local nm="$1" port="$2" started="$3" leftover
    [[ -z "$started" ]] && return 0
    leftover="$(pids_on_port "$port" | tr '\n' ' ')"
    [[ -n "${leftover// /}" ]] && \
      echo "  WARNING: $nm still has a listener on :$port (pid(s): ${leftover% }) — kill with: taskkill //F //T //PID <pid>"
  }
  warn_leftover backend  "$BACKEND_PORT"  "$BACKEND_PID"
  warn_leftover frontend "$FRONTEND_PORT" "$FRONTEND_PID"
  echo "Done."
}
trap cleanup EXIT INT TERM

# --- demo mode: resolve + seed ----------------------------------------
resolve_demo_mode() {
  # Precedence: an explicit DEMO_MODE in the environment wins; otherwise demo
  # mode is always on (no interactive prompt) for D, L, and R.
  if [[ -n "${DEMO_MODE:-}" ]]; then
    case "${DEMO_MODE,,}" in
      1|true|yes|on) DEMO_MODE=true;  DEMO_MODE_ON="yes" ;;
      *)             DEMO_MODE=false; DEMO_MODE_ON="no"  ;;
    esac
    echo ""
    echo "Demo mode: ${DEMO_MODE_ON^^}  (from environment: DEMO_MODE=$DEMO_MODE)"
  else
    # Demo mode is always on for D, L, and R — no interactive prompt.
    DEMO_MODE=true; DEMO_MODE_ON="yes"
    echo "Demo mode: ${DEMO_MODE_ON^^}"
  fi
  export DEMO_MODE
}

seed_demo_evidence() {
  local url="$BACKEND_URL/api/v1/dev/seed-demo-evidence" body
  echo "Demo mode on — seeding demo evidence (POST $url)"
  if ! body="$(curl -s -f -m 20 -X POST -H 'Content-Type: application/json' "$url" 2>/dev/null)"; then
    DEMO_SEED_STATUS="failed"
    DEMO_SEED_DETAIL="backend unreachable or endpoint returned an error"
    echo "  WARNING: demo-evidence seed failed ($DEMO_SEED_DETAIL) — continuing anyway." >&2
    return 0
  fi
  local seeded created dups
  seeded="$(printf '%s' "$body"  | grep -oE '"seeded":[0-9]+'     | grep -oE '[0-9]+')"
  created="$(printf '%s' "$body" | grep -oE '"created":[0-9]+'    | grep -oE '[0-9]+')"
  dups="$(printf '%s' "$body"    | grep -oE '"duplicates":[0-9]+' | grep -oE '[0-9]+')"
  DEMO_SEED_STATUS="ok"
  DEMO_SEED_DETAIL="seeded=${seeded:-?} created=${created:-?} duplicates=${dups:-?}"
  echo "  demo-evidence seed OK — $DEMO_SEED_DETAIL"
}

# --- service starters ---------------------------------------------------
start_backend() {
  local profile="$1"   # "" for the default (Docker) profile, or "dev"
  local mvn="./mvnw"; [[ -x "$BACKEND_DIR/mvnw" ]] || mvn="./mvnw.cmd"
  local demo="${DEMO_MODE:-false}"
  # Always activate the "local" profile so a developer's gitignored
  # backend-java/config/application-local.yml (if present) is picked up. It is a
  # no-op when that file doesn't exist, so this changes nothing for a fresh clone.
  local profiles="local"; [[ -n "$profile" ]] && profiles="$profile,local"
  # BACKEND_EXTRA_JVM_ARGS lets a caller (e.g. run_demo) inject extra JVM args
  # for its invocation only; unset/empty for every other caller, so this is a
  # no-op everywhere except where it's explicitly set beforehand.
  local jvm_args=""
  [[ "$demo" == "true" ]] && jvm_args="-DDEMO_MODE=true"
  [[ -n "${BACKEND_EXTRA_JVM_ARGS:-}" ]] && jvm_args="${jvm_args:+$jvm_args }${BACKEND_EXTRA_JVM_ARGS}"
  if [[ -n "$DRYRUN" ]]; then
    local prof_dry=" -Dspring-boot.run.profiles=$profiles"
    local demo_arg_dry=""; [[ -n "$jvm_args" ]] && demo_arg_dry=" -Dspring-boot.run.jvmArguments=$jvm_args"
    echo "[dry-run] (cd backend-java && $mvn -q spring-boot:run${prof_dry}${demo_arg_dry}) >> $BACKEND_LOG 2>&1 &"
    BACKEND_PID="dryrun"; return 0
  fi
  require_port_free "backend" "$BACKEND_PORT"
  echo "Starting backend (${profile:-default profile}) -> $BACKEND_LOG"
  # DEMO_MODE was resolved by resolve_demo_mode and exported, so the plugin fork
  # inherits it. We ALSO pass it as a JVM system property so it survives the
  # mvnw.cmd -> cmd.exe hop on Windows; Spring resolves application.yml's
  # ${DEMO_MODE:false} from system properties too.
  echo "  demo-mode: $demo  -> pramaan.demo-mode"
  local run_args=(-q spring-boot:run -Dspring-boot.run.profiles="$profiles")
  [[ -n "$jvm_args" ]] && run_args+=(-Dspring-boot.run.jvmArguments="$jvm_args")
  : > "$BACKEND_LOG"
  (
    cd "$BACKEND_DIR"
    exec "$mvn" "${run_args[@]}"
  ) >>"$BACKEND_LOG" 2>&1 &
  BACKEND_PID=$!
  echo "  backend pid $BACKEND_PID"
}

start_frontend() {
  if [[ -n "$DRYRUN" ]]; then
    echo "[dry-run] (cd frontend-react && npm run dev) >> $FRONTEND_LOG 2>&1 &"
    FRONTEND_PID="dryrun"; return 0
  fi
  require_port_free "frontend" "$FRONTEND_PORT"
  echo "Starting frontend -> $FRONTEND_LOG"
  : > "$FRONTEND_LOG"
  ( cd "$FRONTEND_DIR"
    if [[ ! -d node_modules ]]; then
      echo "node_modules missing — running npm install..."
      npm install || exit 1
    fi
    exec npm run dev
  ) >>"$FRONTEND_LOG" 2>&1 &
  FRONTEND_PID=$!
  echo "  frontend pid $FRONTEND_PID"
}

require_docker() {
  [[ -n "$DRYRUN" ]] && { echo "[dry-run] would verify Docker is running"; return 0; }
  if ! docker_running; then
    echo "" >&2
    echo "Docker Desktop isn't running — start it or choose R (Normal, no Docker)." >&2
    exit 1
  fi
}

start_app_layer() {
  # $1: backend profile ("" or "dev")
  local profile="$1"
  start_backend "$profile"
  [[ -n "$DRYRUN" ]] || wait_for_http "$BACKEND_URL/actuator/health" "backend" 180 || exit 1
  if [[ "${DEMO_MODE:-false}" == "true" ]]; then
    if [[ -n "$DRYRUN" ]]; then
      echo "[dry-run] would POST $BACKEND_URL/api/v1/dev/seed-demo-evidence"
    else
      seed_demo_evidence
    fi
  fi
  start_frontend
  [[ -n "$DRYRUN" ]] || wait_for_http "$FRONTEND_URL" "frontend" 120 || exit 1
  if [[ -n "$DRYRUN" ]]; then echo "[dry-run] would poll $BACKEND_URL and $FRONTEND_URL, then wait for Ctrl+C"; return 0; fi

  local demo_line="    Demo    : off"
  if [[ "$DEMO_MODE_ON" == "yes" ]]; then
    case "$DEMO_SEED_STATUS" in
      ok)     demo_line="    Demo    : ON — evidence seeded ($DEMO_SEED_DETAIL)" ;;
      failed) demo_line="    Demo    : ON — seed FAILED ($DEMO_SEED_DETAIL); retry: curl -X POST $BACKEND_URL/api/v1/dev/seed-demo-evidence" ;;
      *)      demo_line="    Demo    : ON" ;;
    esac
  fi

  cat <<EOF

────────────────────────────────────────────────────────
  Pramaan Next is up.
    Backend : $BACKEND_URL        (log: $BACKEND_LOG)
    Frontend: $FRONTEND_URL        (log: $FRONTEND_LOG)
$( [[ -n "$STARTED_COMPOSE" ]] && echo "    Docker  : ${COMPOSE_SERVICES[*]}" )
$demo_line

  Press Ctrl+C to stop everything this script started.
────────────────────────────────────────────────────────
EOF

  # Keep running until interrupted; then the EXIT trap cleans up.
  #
  # Liveness is judged by the *ports/health endpoints*, not by the captured
  # $! — see the note on MONITOR_* above. A service is only considered down
  # after MONITOR_GRACE_FAILS consecutive failed polls, so one slow response
  # (GC pause, HMR rebuild) can't trigger a false shutdown.
  local back_fails=0 front_fails=0 down=""
  while :; do
    sleep "$MONITOR_INTERVAL"
    [[ -n "$CLEANED" ]] && return 0   # trap fired during our sleep — stop quietly

    if http_ok "$BACKEND_URL/actuator/health"; then back_fails=0
    else
      back_fails=$((back_fails + 1))
      echo "  backend health check failed ($back_fails/$MONITOR_GRACE_FAILS) on :$BACKEND_PORT"
    fi

    if http_ok "$FRONTEND_URL"; then front_fails=0
    else
      front_fails=$((front_fails + 1))
      echo "  frontend check failed ($front_fails/$MONITOR_GRACE_FAILS) on :$FRONTEND_PORT"
    fi

    if (( back_fails >= MONITOR_GRACE_FAILS )); then down="backend (:$BACKEND_PORT)"; break; fi
    if (( front_fails >= MONITOR_GRACE_FAILS )); then down="frontend (:$FRONTEND_PORT)"; break; fi
  done
  echo "$down stopped responding — shutting down."
}

run_demo() {
  require_docker
  echo "Demo mode: starting full docker-compose stack..."
  # Windows JVMs map the OS zone "India Standard Time" to the legacy
  # tz-database alias "Asia/Calcutta", which Postgres 16 rejects in the
  # startup packet's "TimeZone" parameter (pgjdbc sends this directly,
  # independent of any JDBC URL "options" override). Force a valid zone for
  # this Docker-Postgres path only — see CLAUDE.md decision log.
  #
  # Option D is the only mode where minio/pgvector actually have somewhere to
  # point at (localhost:9000 / localhost:5434, both started above), so this is
  # also the only place object-store/vector-store default away from
  # filesystem/memory. L and R keep the filesystem/memory defaults from
  # application.yml unless a developer overrides them explicitly.
  BACKEND_EXTRA_JVM_ARGS="-Duser.timezone=Asia/Kolkata -Dpramaan.object-store.driver=minio -Dpramaan.ai.vector-store=pgvector"
  if [[ -n "$DRYRUN" ]]; then
    echo "[dry-run] docker compose up -d"
    echo "[dry-run] wait for pramaan-postgres container health status (PostgreSQL)"
    start_app_layer ""; return 0
  fi
  COMPOSE_SERVICES=( $(compose config --services) )
  compose up -d || { echo "docker compose up failed." >&2; exit 1; }
  STARTED_COMPOSE=1
  wait_for_container_healthy "pramaan-postgres" "PostgreSQL" 120 || exit 1
  start_app_layer ""      # default profile -> Docker Postgres/pgvector/MinIO
}

run_lowmem() {
  require_docker
  echo "Low-mem mode: starting storage containers only: ${STORAGE_SERVICES[*]}"
  if [[ -n "$DRYRUN" ]]; then
    echo "[dry-run] docker compose up -d ${STORAGE_SERVICES[*]}"
    echo "[dry-run] wait for tcp localhost:$COMPOSE_PG_HOST_PORT (PostgreSQL)"
    start_app_layer ""; return 0
  fi
  COMPOSE_SERVICES=( "${STORAGE_SERVICES[@]}" )
  compose up -d "${STORAGE_SERVICES[@]}" || { echo "docker compose up failed." >&2; exit 1; }
  STARTED_COMPOSE=1
  wait_for_port localhost "$COMPOSE_PG_HOST_PORT" "PostgreSQL" 120 || exit 1
  start_app_layer ""      # default profile -> Docker Postgres/pgvector/MinIO
}

run_normal() {
  echo "Normal mode: no Docker. Backend on the 'dev' profile (H2 + filesystem)."
  start_app_layer "dev"
}

# --- menu --------------------------------------------------------------
cat <<'EOF'

Pramaan Next — choose startup mode
  D) Demo    — full docker-compose stack + backend (dev profile pointing
               at Docker infra) + frontend
  L) Low mem — only storage containers from docker-compose.yml (Postgres,
               pgvector, MinIO, Redis if present) + backend + frontend,
               no other containers
  R) Normal  — no Docker. backend on the H2 dev profile + frontend only
  Q) Quit
EOF

read -rp $'\nChoice: ' choice
case "${choice^^}" in
  D|L|R) resolve_demo_mode ;;
esac
case "${choice^^}" in
  D) run_demo ;;
  L) run_lowmem ;;
  R) run_normal ;;
  Q|"") echo "Bye."; trap - EXIT; exit 0 ;;
  *) echo "Unknown choice: $choice" >&2; trap - EXIT; exit 1 ;;
esac
