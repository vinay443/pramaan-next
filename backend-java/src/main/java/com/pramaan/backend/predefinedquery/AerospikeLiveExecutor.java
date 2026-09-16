package com.pramaan.backend.predefinedquery;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Info;
import com.aerospike.client.admin.User;
import com.aerospike.client.cluster.Node;
import com.aerospike.client.policy.AdminPolicy;
import com.aerospike.client.policy.ClientPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pramaan.backend.util.Hashing;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real execution against an Aerospike node for the catalogue's {@code asinfo -v}
 * controls (17 of the 20 Aerospike entries) plus the two {@code asadm -e} controls
 * that have a genuine 1:1 client call: {@code "show users"} (the admin
 * {@link AerospikeClient#queryUsers} query) and {@code "show stat"} (which asadm
 * itself implements as the per-node {@code statistics} info command).
 *
 * <p>{@code ASX-010} ({@code asadm -e "show config"}) is deliberately NOT claimed
 * here — asadm builds that report by aggregating several info contexts
 * (service/network/namespace/security) and formatting them, so there is no single
 * client call that is a faithful 1:1 substitute. It stays on SIMULATED_FALLBACK
 * rather than being approximated.
 */
class AerospikeLiveExecutor implements TechnologyLiveExecutor {

    private static final Pattern ASINFO = Pattern.compile("^asinfo\\s+-v\\s+\"(.+)\"$");
    private static final String SHOW_USERS = "asadm -e \"show users\"";
    private static final String SHOW_STAT = "asadm -e \"show stat\"";
    private static final String NAMESPACE_PLACEHOLDER = "${AEROSPIKE_NAMESPACE:-test}";

    private final String host;
    private final int port;
    private final String namespace;
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile AerospikeClient client;

    AerospikeLiveExecutor(String host, int port, String namespace) {
        this.host = host;
        this.port = port;
        this.namespace = namespace;
    }

    @Override
    public boolean supports(PredefinedQuery q) {
        if (!"Aerospike".equals(q.technology()) || q.command() == null) {
            return false;
        }
        String cmd = q.command().trim();
        return ASINFO.matcher(cmd).matches() || SHOW_USERS.equals(cmd) || SHOW_STAT.equals(cmd);
    }

    @Override
    public PredefinedQueryExecutor.Output execute(PredefinedQuery q, String applicationSlug, Instant asOf) {
        String cmd = q.command().trim();
        try {
            if (SHOW_USERS.equals(cmd)) {
                return usersOutput(q, applicationSlug, asOf);
            }
            if (SHOW_STAT.equals(cmd)) {
                return infoOutput(q, applicationSlug, asOf, "statistics", cmd);
            }
            Matcher m = ASINFO.matcher(cmd);
            if (m.matches()) {
                String param = m.group(1).replace(NAMESPACE_PLACEHOLDER, namespace);
                return infoOutput(q, applicationSlug, asOf, param, "asinfo -v \"" + param + "\"");
            }
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "live execution failed for control " + q.controlId() + ": " + ex.getMessage(), ex);
        }
        // supports() gates every call reaching here, so this only fires on a
        // programming error (a new command form added to supports() without a
        // matching branch above).
        throw new IllegalStateException("control " + q.controlId() + " has no Aerospike LIVE handler for: " + cmd);
    }

    private PredefinedQueryExecutor.Output infoOutput(
            PredefinedQuery q, String applicationSlug, Instant asOf, String infoParam, String executed) {
        Node node = anyNode();
        String response = Info.request(node, infoParam);
        return buildOutput(q, applicationSlug, asOf, executed, node, response);
    }

    private PredefinedQueryExecutor.Output usersOutput(PredefinedQuery q, String applicationSlug, Instant asOf) {
        Node node = anyNode();
        List<User> users = client().queryUsers(new AdminPolicy());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", u.name);
            row.put("roles", u.roles);
            rows.add(row);
        }
        return buildOutput(q, applicationSlug, asOf, SHOW_USERS, node, rows);
    }

    private PredefinedQueryExecutor.Output buildOutput(
            PredefinedQuery q, String applicationSlug, Instant asOf, String executed, Node node, Object result) {
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
        body.put("node", node.getHost().name + ":" + node.getHost().port);
        body.put("executed", executed);
        body.put("result", result);

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
        metadata.put("digest.short", Hashing.sha256Hex(content).substring(0, 12));

        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("technology", "aerospike");
        tags.put("collectionMethod", "predefined-query");
        tags.put("evidenceType", q.evidenceType() == null ? "query-output" : q.evidenceType());
        tags.put("controlFamily", q.controlFamily() == null ? "" : q.controlFamily());
        tags.put("agent", "PREDEFINED_QUERY");
        tags.put("live", "true");
        tags.put("executionMode", "LIVE");

        return new PredefinedQueryExecutor.Output(
                new String(content, StandardCharsets.UTF_8), "application/json", metadata, tags);
    }

    private Node anyNode() {
        Node[] nodes = client().getNodes();
        if (nodes.length == 0) {
            throw new IllegalStateException("no Aerospike nodes reachable at " + host + ":" + port);
        }
        return nodes[0];
    }

    /** Lazily connects on first use so an unreachable Aerospike never blocks app
     *  startup — only the controls that actually need it fail, and only when run. */
    private AerospikeClient client() {
        AerospikeClient c = client;
        if (c != null && c.isConnected()) {
            return c;
        }
        synchronized (this) {
            if (client != null) {
                if (client.isConnected()) {
                    return client;
                }
                client.close();
            }
            ClientPolicy policy = new ClientPolicy();
            policy.timeout = 1000;
            policy.failIfNotConnected = true;
            client = new AerospikeClient(policy, host, port);
            return client;
        }
    }
}
