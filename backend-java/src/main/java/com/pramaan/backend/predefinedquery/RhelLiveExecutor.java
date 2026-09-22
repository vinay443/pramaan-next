package com.pramaan.backend.predefinedquery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.pramaan.backend.util.Hashing;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Real execution against a RHEL host over SSH for the catalogue's RH8-*
 * / RH9-* controls. Every one of those 16 entries is a plain shell command run on the
 * target host ({@code cat /etc/redhat-release}, {@code getenforce}, {@code
 * systemctl is-active firewalld}, ...) — unlike the DB executors, there is no
 * JDBC wire protocol here, so this connects over SSH (via JSch) and execs the
 * command directly.
 *
 * <p>Because the commands are fixed OS shell invocations rather than a SQL
 * grammar, the defensive guard here is an exact-match allow-list of the 16
 * known catalogue commands (see {@link #ALLOWED_COMMANDS}) rather than a
 * regex like {@code YugabyteLiveExecutor}'s {@code READ_ONLY} pattern — a
 * future catalogue edit cannot make this executor run anything beyond those
 * exact strings just by being marked LIVE.
 *
 * <p>One instance covers both "Red Hat Enterprise Linux 8.x" and "Red Hat
 * Enterprise Linux 9.x" catalogue entries: in this demo setup a single SSH
 * target plausibly stands in for either OS version, and the 16 commands are
 * read-only OS/security-posture checks with no version-specific behavior that
 * would require separate hosts.
 */
class RhelLiveExecutor implements TechnologyLiveExecutor {

    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            "cat /etc/redhat-release",
            "update-crypto-policies --show 2>/dev/null || true",
            "getenforce 2>/dev/null || sestatus 2>/dev/null || true",
            "systemctl is-active firewalld 2>/dev/null || true",
            "systemctl is-active auditd 2>/dev/null || true",
            "grep -Ei \"^\\s*PermitRootLogin\" /etc/ssh/sshd_config 2>/dev/null || true",
            "grep -Ei \"^\\s*PasswordAuthentication\" /etc/ssh/sshd_config 2>/dev/null || true",
            "dnf updateinfo list security installed 2>/dev/null || yum updateinfo list security installed 2>/dev/null || true",
            "fips-mode-setup --check 2>/dev/null || cat /proc/sys/crypto/fips_enabled 2>/dev/null || true");

    private static final int CONNECT_TIMEOUT_MS = 2500;

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String privateKeyPath;
    private final ObjectMapper mapper = new ObjectMapper();

    RhelLiveExecutor(String host, int port, String username, String password, String privateKeyPath) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.privateKeyPath = privateKeyPath;
    }

    @Override
    public boolean supports(PredefinedQuery q) {
        return q.technology() != null && q.technology().startsWith("Red Hat Enterprise Linux")
                && q.command() != null && !q.command().isBlank()
                && ALLOWED_COMMANDS.contains(q.command().trim());
    }

    @Override
    public PredefinedQueryExecutor.Output execute(PredefinedQuery q, String applicationSlug, Instant asOf) {
        String cmd = q.command().trim();
        String output;
        Session session = null;
        try {
            session = openSession();
            output = exec(session, cmd);
        } catch (JSchException | IOException ex) {
            throw new IllegalStateException(
                    "live execution failed for control " + q.controlId() + " over SSH to " + host + ":" + port
                            + ": " + ex.getMessage(), ex);
        } finally {
            if (session != null) {
                session.disconnect();
            }
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
        body.put("command", cmd);
        body.put("output", output.trim());

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
        tags.put("technology", q.technology().contains("9.x") ? "rhel9" : "rhel8");
        tags.put("collectionMethod", "predefined-query");
        tags.put("evidenceType", q.evidenceType() == null ? "query-output" : q.evidenceType());
        tags.put("controlFamily", q.controlFamily() == null ? "" : q.controlFamily());
        tags.put("agent", "PREDEFINED_QUERY");
        tags.put("live", "true");
        tags.put("executionMode", "LIVE");

        return new PredefinedQueryExecutor.Output(
                new String(content, StandardCharsets.UTF_8), "application/json", metadata, tags);
    }

    private Session openSession() throws JSchException {
        JSch jsch = new JSch();
        if (privateKeyPath != null && !privateKeyPath.isBlank()) {
            jsch.addIdentity(privateKeyPath);
        }
        Session session = jsch.getSession(username, host, port);
        if (password != null && !password.isBlank()) {
            session.setPassword(password);
        }
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.setTimeout(CONNECT_TIMEOUT_MS);
        session.connect(CONNECT_TIMEOUT_MS);
        return session;
    }

    private static String exec(Session session, String command) throws JSchException, IOException {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        try {
            channel.setCommand(command);
            channel.setInputStream(null);
            channel.setErrStream(System.err);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream in = channel.getInputStream()) {
                channel.connect(CONNECT_TIMEOUT_MS);
                in.transferTo(buffer);
            }
            while (!channel.isClosed()) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return buffer.toString(StandardCharsets.UTF_8);
        } finally {
            channel.disconnect();
        }
    }
}
