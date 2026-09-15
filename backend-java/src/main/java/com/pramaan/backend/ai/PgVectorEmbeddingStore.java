package com.pramaan.backend.ai;

import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PostgreSQL + pgvector embedding store. Connects to the dedicated {@code pgvector}
 * container/DB (not the app's primary Flyway-managed database, which has no
 * {@code vector} extension) — see {@link com.pramaan.backend.ai.AiConfig}. The
 * {@code vector} extension and the {@code evidence_embedding} table are created
 * on demand by {@link #ensureSchema()}, not by Flyway, so pgvector's DDL only runs
 * when {@code pramaan.ai.vector-store=pgvector} is actually selected. Nearest-
 * neighbour search uses the cosine distance operator {@code <=>}; similarity score
 * is {@code 1 - distance}.
 */
public class PgVectorEmbeddingStore implements EmbeddingStore {

    private final JdbcTemplate jdbc;
    private final int dimension;

    public PgVectorEmbeddingStore(JdbcTemplate jdbc, int dimension) {
        this.jdbc = jdbc;
        this.dimension = dimension;
    }

    /**
     * Create the extension and table if absent. Called at wiring time so the pgvector
     * DDL only runs when {@code pramaan.ai.vector-store=pgvector} is actually selected
     * (the default Flyway path stays PostgreSQL-image agnostic).
     */
    public void ensureSchema() {
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS evidence_embedding (
                    evidence_id      UUID PRIMARY KEY,
                    application_slug VARCHAR(120) NOT NULL,
                    framework        VARCHAR(120) NOT NULL,
                    control_id       VARCHAR(120) NOT NULL,
                    sha256           CHAR(64),
                    embedding        vector(%d) NOT NULL
                )
                """.formatted(dimension));
    }

    @Override
    public void upsert(Entry e) {
        jdbc.update("""
                INSERT INTO evidence_embedding
                    (evidence_id, application_slug, framework, control_id, sha256, embedding)
                VALUES (?, ?, ?, ?, ?, ?::vector)
                ON CONFLICT (evidence_id) DO UPDATE SET
                    application_slug = EXCLUDED.application_slug,
                    framework        = EXCLUDED.framework,
                    control_id       = EXCLUDED.control_id,
                    sha256           = EXCLUDED.sha256,
                    embedding        = EXCLUDED.embedding
                """,
                e.evidenceId(), e.applicationSlug(), e.framework(), e.controlId(), e.sha256(),
                literal(e.vector()));
    }

    @Override
    public void clear() {
        jdbc.update("DELETE FROM evidence_embedding");
    }

    @Override
    public long count() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM evidence_embedding", Long.class);
        return n == null ? 0 : n;
    }

    @Override
    public List<Match> search(float[] query, int limit) {
        return jdbc.query("""
                SELECT evidence_id, application_slug, framework, control_id, sha256,
                       1 - (embedding <=> ?::vector) AS score
                FROM evidence_embedding
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """,
                (rs, i) -> new Match(
                        new Entry(rs.getObject("evidence_id", UUID.class),
                                rs.getString("application_slug"), rs.getString("framework"),
                                rs.getString("control_id"), rs.getString("sha256"), null),
                        rs.getDouble("score")),
                literal(query), literal(query), Math.max(1, limit));
    }

    @Override
    public String driver() {
        return "pgvector";
    }

    private static String literal(float[] v) {
        StringJoiner sj = new StringJoiner(",", "[", "]");
        for (float x : v) {
            sj.add(Float.toString(x));
        }
        return sj.toString();
    }
}
