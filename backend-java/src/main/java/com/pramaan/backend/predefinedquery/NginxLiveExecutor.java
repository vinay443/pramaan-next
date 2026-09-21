package com.pramaan.backend.predefinedquery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pramaan.backend.util.Hashing;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Real execution against an NGINX target for two kinds of catalogue control:
 *
 * <ul>
 *   <li>{@code grep -R "<directive>" /etc/nginx ...} (NGX-003..NGX-007) and
 *       {@code nginx -T} (MW-001) — read live off the exact {@code nginx.conf} the
 *       compose {@code nginx} service is running (bind-mounted from the same host
 *       path this class reads, {@code pramaan.nginx.config-path}), then grep it the
 *       same way the catalogue command does.
 *   <li>Connection-metrics controls whose command targets the stub_status module
 *       ({@code nginx_status}/{@code stub_status}) — a real HTTP GET against the
 *       running server's {@code /nginx_status} endpoint, parsed as-is.
 * </ul>
 *
 * <p>{@code NGX-001} ({@code nginx -v}), {@code NGX-002} ({@code nginx -t}), and
 * {@code NGX-008} ({@code find /etc/nginx/sites-enabled ...}) are deliberately NOT
 * claimed here — none has a faithful 1:1 substitute without shell exec into the
 * container (this executor only reads a host-mounted file and calls the HTTP status
 * endpoint, mirroring how {@code PostgresLiveExecutor}/{@code AerospikeLiveExecutor}
 * only ever reach their target over the network, never via exec). They stay on
 * SIMULATED_FALLBACK rather than being approximated.
 */
class NginxLiveExecutor implements TechnologyLiveExecutor {

    private static final Pattern GREP = Pattern.compile("^grep\\s+-R\\s+\"([^\"]+)\"\\s+/etc/nginx\\b.*$");
    private static final Pattern READING_WRITING_WAITING =
            Pattern.compile("Reading:\\s*(\\d+)\\s*Writing:\\s*(\\d+)\\s*Waiting:\\s*(\\d+)");
    private static final String NGINX_T = "nginx -T";

    private final String host;
    private final int port;
    private final String statusPath;
    private final Path configPath;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    NginxLiveExecutor(String host, int port, String statusPath, String configPath) {
        this.host = host;
        this.port = port;
        this.statusPath = statusPath;
        this.configPath = Path.of(configPath);
    }

    @Override
    public boolean supports(PredefinedQuery q) {
        if (!"NGINX".equals(q.technology()) || q.command() == null) {
            return false;
        }
        String cmd = q.command().trim();
        return NGINX_T.equals(cmd) || GREP.matcher(cmd).matches() || isStubStatusCommand(cmd);
    }

    @Override
    public PredefinedQueryExecutor.Output execute(PredefinedQuery q, String applicationSlug, Instant asOf) {
        String cmd = q.command().trim();
        try {
            if (NGINX_T.equals(cmd)) {
                return configOutput(q, applicationSlug, asOf);
            }
            if (isStubStatusCommand(cmd)) {
                return statusOutput(q, applicationSlug, asOf);
            }
            Matcher m = GREP.matcher(cmd);
            if (m.matches()) {
                return grepOutput(q, applicationSlug, asOf, m.group(1));
            }
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "live execution failed for control " + q.controlId() + ": " + ex.getMessage(), ex);
        }
        // supports() gates every call reaching here, so this only fires on a
        // programming error (a new command form added to supports() without a
        // matching branch above).
        throw new IllegalStateException("control " + q.controlId() + " has no NGINX LIVE handler for: " + cmd);
    }

    private static boolean isStubStatusCommand(String cmd) {
        return cmd.contains("nginx_status") || cmd.contains("stub_status");
    }

    /** {@code nginx -T} equivalent: the actual live config content, not a rewrite —
     *  it's the same file bind-mounted into the running container. */
    private PredefinedQueryExecutor.Output configOutput(PredefinedQuery q, String applicationSlug, Instant asOf) {
        String config = readConfig();
        return buildOutput(q, applicationSlug, asOf,
                "read live config (mounted into the nginx container at /etc/nginx/nginx.conf): " + configPath,
                config);
    }

    private PredefinedQueryExecutor.Output grepOutput(
            PredefinedQuery q, String applicationSlug, Instant asOf, String pattern) {
        String config = readConfig();
        List<String> matches = config.lines()
                .map(String::trim)
                .filter(line -> line.contains(pattern))
                .toList();
        return buildOutput(q, applicationSlug, asOf, "grep -R \"" + pattern + "\" " + configPath, matches);
    }

    private String readConfig() {
        try {
            return Files.readString(configPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read nginx config at " + configPath + ": " + e.getMessage(), e);
        }
    }

    private PredefinedQueryExecutor.Output statusOutput(PredefinedQuery q, String applicationSlug, Instant asOf) {
        String url = "http://" + host + ":" + port + statusPath;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("nginx status request to " + url + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("nginx status request to " + url + " was interrupted", e);
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "nginx status endpoint " + url + " returned HTTP " + response.statusCode());
        }
        return buildOutput(q, applicationSlug, asOf, "GET " + url, parseStubStatus(response.body()));
    }

    /** Parses the standard ngx_http_stub_status_module text response:
     *  <pre>
     *  Active connections: 1
     *  server accepts handled requests
     *   5 5 10
     *  Reading: 0 Writing: 1 Waiting: 0
     *  </pre> */
    private static Map<String, Object> parseStubStatus(String body) {
        Map<String, Object> parsed = new LinkedHashMap<>();
        for (String rawLine : body.lines().toList()) {
            String line = rawLine.trim();
            if (line.startsWith("Active connections:")) {
                parsed.put("activeConnections", Long.parseLong(line.substring("Active connections:".length()).trim()));
            } else if (line.matches("^\\d+\\s+\\d+\\s+\\d+$")) {
                String[] parts = line.split("\\s+");
                parsed.put("accepts", Long.parseLong(parts[0]));
                parsed.put("handled", Long.parseLong(parts[1]));
                parsed.put("requests", Long.parseLong(parts[2]));
            } else {
                Matcher m = READING_WRITING_WAITING.matcher(line);
                if (m.matches()) {
                    parsed.put("reading", Long.parseLong(m.group(1)));
                    parsed.put("writing", Long.parseLong(m.group(2)));
                    parsed.put("waiting", Long.parseLong(m.group(3)));
                }
            }
        }
        return parsed;
    }

    private PredefinedQueryExecutor.Output buildOutput(
            PredefinedQuery q, String applicationSlug, Instant asOf, String executed, Object result) {
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
        body.put("target", host + ":" + port);
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
        tags.put("technology", "nginx");
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
