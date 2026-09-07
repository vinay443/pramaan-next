-- Vector store bootstrap. Runs once on first container start (empty data volume).

CREATE EXTENSION IF NOT EXISTS vector;

CREATE SCHEMA IF NOT EXISTS embeddings;

-- No embedding tables yet. They will be created by the service that owns indexing.
