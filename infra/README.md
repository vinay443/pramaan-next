# infra/

Local development infrastructure, orchestrated by the repo-root `docker-compose.yml`.

| Service      | Image                     | Host port | Purpose                        |
|--------------|---------------------------|-----------|--------------------------------|
| `postgres`   | `postgres:16`             | 5433      | Primary application database   |
| `pgvector`   | `pgvector/pgvector:pg16`  | 5434      | Vector store (pgvector)        |
| `minio`      | `minio/minio`             | 9000/9001 | S3-compatible object storage   |

Init SQL under `postgres/init/` and `pgvector/init/` runs only on first start of an
empty data volume. To re-run it: `docker compose down -v` then `docker compose up -d`.

Credentials are local-only defaults and defined in `docker-compose.yml` /
`.env` at the repo root.
