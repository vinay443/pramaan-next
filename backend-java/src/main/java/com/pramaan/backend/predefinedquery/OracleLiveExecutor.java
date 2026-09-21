package com.pramaan.backend.predefinedquery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pramaan.backend.util.Hashing;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Real execution against an Oracle target for the catalogue's Oracle controls.
 * All 16 current entries (DB-004, PCI-003, ORX-001..014) are plain {@code
 * SELECT} statements against dictionary views ({@code dba_*}) or dynamic
 * performance views ({@code v$*}) — no PL/SQL block, and nothing needs
 * {@code sqlplus}/RMAN, so every one of them is claimed and answered for real.
 *
 * <p><b>Required privilege level:</b> several controls (ORX-006 {@code
 * dba_users}, ORX-007/PCI-003 {@code dba_role_privs}, ORX-008/009 {@code
 * dba_tablespaces}, ORX-010/013 {@code v$session}, ORX-011 {@code
 * v$resource_limit}, ORX-012 {@code v$instance}, ORX-014 {@code dba_objects})
 * query dictionary/dynamic-performance views that are NOT visible to an
 * ordinary schema user — they require either {@code SELECT_CATALOG_ROLE} (or
 * equivalent per-view grants) or full DBA privilege. This connects as {@code
 * SYSTEM} (via {@code pramaan.oracle.user}, see {@code application.yml}),
 * which has DBA privileges out of the box on the {@code gvenzl/oracle-free}
 * dev image — this is dev/demo-only infra, not a least-privilege production
 * setup; a real deployment should instead grant {@code SELECT_CATALOG_ROLE}
 * to a dedicated read-only monitoring user.
 */
class OracleLiveExecutor implements TechnologyLiveExecutor {

    /** Only SELECT is permitted against the real database — a future catalogue
     *  edit must not be able to run a destructive statement just by being marked LIVE.
     *  (Oracle has no bare SHOW statement, unlike Postgres/MySQL/YSQL, so unlike
     *  those executors this only allows SELECT.) */
    private static final Pattern READ_ONLY = Pattern.compile("^\\s*SELECT\\b", Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    OracleLiveExecutor(String host, int port, String serviceName, String user, String password) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("oracle.jdbc.OracleDriver");
        dataSource.setUrl("jdbc:oracle:thin:@//" + host + ":" + port + "/" + serviceName);
        dataSource.setUsername(user);
        dataSource.setPassword(password);
        Properties connectionProps = new Properties();
        connectionProps.setProperty("oracle.net.CONNECT_TIMEOUT", "2000");
        connectionProps.setProperty("oracle.jdbc.ReadTimeout", "5000");
        dataSource.setConnectionProperties(connectionProps);
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public boolean supports(PredefinedQuery q) {
        return "Oracle".equals(q.technology()) && q.command() != null && !q.command().isBlank();
    }

    @Override
    public PredefinedQueryExecutor.Output execute(PredefinedQuery q, String applicationSlug, Instant asOf) {
        if (!READ_ONLY.matcher(q.command()).find()) {
            throw new IllegalStateException(
                    "refusing to run control " + q.controlId() + " in LIVE mode: only read-only "
                            + "SELECT commands may be executed against the real database, got: "
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
        tags.put("technology", "oracle");
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
