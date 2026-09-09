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

# --- service starters ---------------------------------------------------
start_backend() {
  local profile="$1"   # "" for the default (Docker) profile, or "dev"
  local mvn="./mvnw"; [[ -x "$BACKEND_DIR/mvnw" ]] || mvn="./mvnw.cmd"
  if [[ -n "$DRYRUN" ]]; then
    if [[ -n "$profile" ]]; then
      echo "[dry-run] (cd backend-java && $mvn -q spring-boot:run -Dspring-boot.run.profiles=$profile) >> $BACKEND_LOG 2>&1 &"
    else
      echo "[dry-run] (cd backend-java && $mvn -q spring-boot:run) >> $BACKEND_LOG 2>&1 &"
    fi
    BACKEND_PID="dryrun"; return 0
  fi
  echo "Starting backend (${profile:-default profile}) -> $BACKEND_LOG"
  : > "$BACKEND_LOG"
  (
    cd "$BACKEND_DIR"
    if [[ -n "$profile" ]]; then
      exec "$mvn" -q spring-boot:run -Dspring-boot.run.profiles="$profile"
    else
      exec "$mvn" -q spring-boot:run
    fi
  ) >>"$BACKEND_LOG" 2>&1 &
  BACKEND_PID=$!
  echo "  backend pid $BACKEND_PID"
}

start_frontend() {
  if [[ -n "$DRYRUN" ]]; then
    echo "[dry-run] (cd frontend-react && npm run dev) >> $FRONTEND_LOG 2>&1 &"
    FRONTEND_PID="dryrun"; return 0
  fi
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
  start_frontend
  [[ -n "$DRYRUN" ]] || wait_for_http "$FRONTEND_URL" "frontend" 120 || exit 1
  if [[ -n "$DRYRUN" ]]; then echo "[dry-run] would poll $BACKEND_URL and $FRONTEND_URL, then wait for Ctrl+C"; return 0; fi

  cat <<EOF

────────────────────────────────────────────────────────
  Pramaan Next is up.
    Backend : $BACKEND_URL        (log: $BACKEND_LOG)
    Frontend: $FRONTEND_URL        (log: $FRONTEND_LOG)
$( [[ -n "$STARTED_COMPOSE" ]] && echo "    Docker  : ${COMPOSE_SERVICES[*]}" )

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
  if [[ -n "$DRYRUN" ]]; then
    echo "[dry-run] docker compose up -d"
    echo "[dry-run] wait for tcp localhost:$COMPOSE_PG_HOST_PORT (PostgreSQL)"
    start_app_layer ""; return 0
  fi
  COMPOSE_SERVICES=( $(compose config --services) )
  compose up -d || { echo "docker compose up failed." >&2; exit 1; }
  STARTED_COMPOSE=1
  wait_for_port localhost "$COMPOSE_PG_HOST_PORT" "PostgreSQL" 120 || exit 1
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
  D) run_demo ;;
  L) run_lowmem ;;
  R) run_normal ;;
  Q|"") echo "Bye."; trap - EXIT; exit 0 ;;
  *) echo "Unknown choice: $choice" >&2; trap - EXIT; exit 1 ;;
esac
