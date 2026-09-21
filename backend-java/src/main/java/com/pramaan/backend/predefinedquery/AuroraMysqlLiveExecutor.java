package com.pramaan.backend.predefinedquery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pramaan.backend.util.Hashing;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Real execution against a MySQL-protocol target for the catalogue's Aurora MySQL
 * controls. There is no official local Aurora image, so this connects to plain
 * {@code mysql:8.0} (docker-compose's {@code aurora-mysql} service) — Aurora MySQL
 * is wire/protocol compatible, and every current catalogue entry is answerable via
 * {@code SHOW VARIABLES}/{@code SHOW STATUS}/{@code information_schema}, all of
 * which behave identically on both.
 *
 * <p>Deliberately NOT claimed here: any command referencing an actual
 * Aurora-managed surface with no plain-MySQL equivalent — replica lag
 * ({@code information_schema.replica_host_status}, an Aurora-only view),
 * failover configuration, or the {@code mysql.rds_*} stored procedures Aurora
 * exposes for cluster administration. None of those exist in the catalogue
 * today, but if one is added later it must stay on SIMULATED_FALLBACK rather
 * than being answered against plain MySQL and mislabeled LIVE — {@link
 * #AURORA_ONLY} exists so that happens automatically instead of by omission.
 */
class AuroraMysqlLiveExecutor implements TechnologyLiveExecutor {

    /** Only SELECT/SHOW are permitted against the real database — a future catalogue
     *  edit must not be able to run a destructive statement just by being marked LIVE. */
    private static final Pattern READ_ONLY = Pattern.compile("^\\s*(SELECT|SHOW)\\b", Pattern.CASE_INSENSITIVE);

    /** Surfaces that only exist on real Aurora, not plain MySQL — see class Javadoc. */
    private static final Pattern AURORA_ONLY = Pattern.compile(
            "aurora_|mysql\\.rds_|replica_host_status", Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    AuroraMysqlLiveExecutor(String host, int port, String database, String user, String password) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?connectTimeout=2000&socketTimeout=5000&useSSL=false&allowPublicKeyRetrieval=true");
        dataSource.setUsername(user);
        dataSource.setPassword(password);
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public boolean supports(PredefinedQuery q) {
        if (!"Aurora MySQL".equals(q.technology()) || q.command() == null || q.command().isBlank()) {
            return false;
        }
        return !AURORA_ONLY.matcher(q.command()).find();
    }

    @Override
    public PredefinedQueryExecutor.Output execute(PredefinedQuery q, String applicationSlug, Instant asOf) {
        if (!READ_ONLY.matcher(q.command()).find()) {
            throw new IllegalStateException(
                    "refusing to run control " + q.controlId() + " in LIVE mode: only read-only "
                            + "SELECT/SHOW commands may be executed against the real database, got: "
                            + q.command());
        }

        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(q.command());
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "live execution failed for control " + q.controlId() + ": " + ex.getMessage(), ex);
        }

        String day = asOf.atZone(ZoneOffset.UTC).toLocalDate().toString();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("collector", "predefined-query");
        body.put("technology", q.technology());
        body.put("application", applicationSlug);
        body.put("controlId", q.controlId());
        body.put("controlName", q.controlName());
        body.put("controlFamily", q.controlFamily());
        body.put("asOf", day);
        body.put("live", true);
        body.put("command", q.command());
        body.put("rowCount", rows.size());
        body.put("rows", rows);

        byte[] content;
        try {
            content = mapper.writeValueAsBytes(body);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize live result for control " + q.controlId(), e);
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("live", "true");
        metadata.put("executionMode", "LIVE");
        metadata.put("collector", "predefined-query");
        metadata.put("technology", q.technology());
        metadata.put("controlFamily", q.controlFamily());
        metadata.put("rowCount", String.valueOf(rows.size()));
        metadata.put("digest.short", Hashing.sha256Hex(content).substring(0, 12));

        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("technology", "aurora-mysql");
        tags.put("collectionMethod", "predefined-query");
        tags.put("evidenceType", q.evidenceType() == null ? "query-output" : q.evidenceType());
        tags.put("controlFamily", q.controlFamily() == null ? "" : q.controlFamily());
        tags.put("agent", "PREDEFINED_QUERY");
        tags.put("live", "true");
        tags.put("executionMode", "LIVE");

        return new PredefinedQueryExecutor.Output(
                new String(content, StandardCharsets.UTF_8), "application/json", metadata, tags);
    }
}
