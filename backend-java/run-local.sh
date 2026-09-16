#!/usr/bin/env bash
# Starts the backend against the Docker storage stack, waiting for Postgres
# to actually accept connections first (not just for the container to start).
#
# Do NOT chain `docker compose up -d && ./mvnw spring-boot:run` manually —
# `docker compose up -d` returns as soon as the container process starts, not
# when Postgres is ready to accept connections, so Flyway can race it on a
# cold start (`FlywaySqlException: Connection to localhost:5433 refused`).
# Use this script instead.
#
# Usage: ./run-local.sh [extra mvnw args...]
#   PRAMAAN_PREDEFINED_QUERIES_MODE=LIVE ./run-local.sh
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

docker compose up -d postgres pgvector minio

echo "Waiting for Postgres to accept connections..."
until docker exec pramaan-postgres pg_isready -U pramaan -d pramaan >/dev/null 2>&1; do
  sleep 1
done
echo "Postgres is ready."

cd backend-java
exec ./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.jvmArguments=-Duser.timezone=UTC \
  "$@"
