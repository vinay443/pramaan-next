package com.pramaan.backend.evidence;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Use Case 3 — the single naming convention and metadata-tag set applied to every
 * evidence record, whether scheduler-collected, bulk-uploaded, or produced by an
 * on-demand predefined query.
 *
 * <p>Name: {@code {sourceApp}_{controlCode}_{evidenceType}_{yyyyMMdd}_{seq}}
 * (e.g. {@code payments_DB-TLS-IN-TRANSIT_DB-CONFIG_20260908_001}). Deterministic
 * for a given record: {@code seq} is the record's version number, zero-padded.
 */
public final class EvidenceNaming {

    /** Canonical tag keys. Values written by this helper always win over caller-supplied tags. */
    public static final String TAG_APPLICATION = "application";
    public static final String TAG_TECHNOLOGY = "technology";
    public static final String TAG_CONTROL = "control";
    public static final String TAG_FRAMEWORK = "framework";
    public static final String TAG_FRAMEWORKS = "frameworks";
    public static final String TAG_EVIDENCE_TYPE = "evidenceType";
    public static final String TAG_NAME = "name";
    public static final String TAG_SOURCE_TITLE = "sourceTitle";
    public static final String TAG_COLLECTION_METHOD = "collectionMethod";
    public static final String TAG_VERSION = "version";

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private EvidenceNaming() {}

    /** The canonical evidence name. {@code seq} is normally the record version number. */
    public static String standardName(String application, String controlCode, String evidenceType,
                                      Instant collectedAt, int seq) {
        return String.join("_",
                slug(application, "app"),
                token(controlCode, "CONTROL"),
                token(evidenceType, "GENERAL"),
                STAMP.format(collectedAt == null ? Instant.EPOCH : collectedAt),
                String.format(Locale.ROOT, "%03d", Math.max(seq, 1)));
    }

    /**
     * The canonical tag map for a record. Merge these over any caller-supplied tags.
     *
     * @param frameworks every framework the control maps to (see {@link ControlFrameworkCatalog});
     *                   the first entry is treated as the primary {@code framework}.
     */
    public static Map<String, String> standardTags(String application, String technology,
                                                   List<String> frameworks, String controlId,
                                                   String evidenceType, String name,
                                                   String sourceTitle, String collectionMethod,
                                                   int version) {
        List<String> fw = frameworks == null ? List.of() : frameworks;
        Map<String, String> t = new LinkedHashMap<>();
        t.put(TAG_APPLICATION, slug(application, "app"));
        t.put(TAG_TECHNOLOGY, lower(technology, "unknown"));
        t.put(TAG_CONTROL, token(controlId, "CONTROL"));
        t.put(TAG_FRAMEWORK, token(fw.isEmpty() ? null : fw.get(0), "UNSPECIFIED"));
        t.put(TAG_FRAMEWORKS, fw.isEmpty() ? "UNSPECIFIED" : String.join(",", fw));
        t.put(TAG_EVIDENCE_TYPE, token(evidenceType, "GENERAL"));
        t.put(TAG_NAME, name == null || name.isBlank() ? "" : name.trim());
        if (sourceTitle != null && !sourceTitle.isBlank()) {
            t.put(TAG_SOURCE_TITLE, sourceTitle.trim());
        }
        t.put(TAG_COLLECTION_METHOD, lower(collectionMethod, "manual"));
        t.put(TAG_VERSION, "v" + Math.max(version, 1));
        return t;
    }

    /**
     * Evidence classification for the name/tag. Explicit {@code evidenceType} tag or
     * metadata wins; otherwise derived from the control-code prefix, then the source system.
     */
    public static String resolveEvidenceType(Map<String, String> tags, Map<String, String> metadata,
                                             String controlId, String sourceSystem) {
        String explicit = pick(tags, TAG_EVIDENCE_TYPE);
        if (explicit == null) explicit = pick(metadata, TAG_EVIDENCE_TYPE);
        if (explicit == null) explicit = pick(metadata, "evidence_type");
        if (explicit != null) return token(explicit, "GENERAL");

        String c = controlId == null ? "" : controlId.trim().toUpperCase(Locale.ROOT);
        if (c.startsWith("OS-")) return "HOST-CONFIG";
        if (c.startsWith("DB-")) return "DB-CONFIG";
        if (c.startsWith("MW-")) return "MIDDLEWARE-CONFIG";
        if (c.startsWith("TLS-")) return "TLS-SCAN";
        if (c.contains("CHG") || c.contains("CHANGE")) return "CHANGE-TICKET";
        if (c.contains("SDLC") || c.startsWith("PCI-DSS-6")) return "CODE-REVIEW";

        String s = sourceSystem == null ? "" : sourceSystem.toUpperCase(Locale.ROOT);
        if (s.contains("JIRA") || s.contains("CHANGE")) return "CHANGE-TICKET";
        if (s.contains("GITHUB") || s.contains("GITLAB")) return "CODE-REVIEW";
        if (s.contains("AGENT")) return "AGENT-SCAN";
        return "GENERAL";
    }

    /**
     * Best-effort technology resolution from an ingest request's tags/metadata, falling
     * back to the source-system suffix (e.g. {@code AGENT_DATABASE_POSTGRESQL -> postgresql}).
     */
    public static String resolveTechnology(Map<String, String> tags, Map<String, String> metadata,
                                           String sourceSystem) {
        String fromTag = pick(tags, TAG_TECHNOLOGY);
        if (fromTag != null) return lower(fromTag, "unknown");
        String fromMeta = pick(metadata, "technology");
        if (fromMeta != null) return lower(fromMeta, "unknown");
        if (sourceSystem != null) {
            String s = sourceSystem.toLowerCase(Locale.ROOT);
            for (String tech : new String[]{"postgresql", "mysql", "nginx", "tomcat", "linux", "tls"}) {
                if (s.contains(tech)) return tech;
            }
        }
        return "unknown";
    }

    /** Collection method from an ingest request, defaulting to {@code manual}. */
    public static String resolveCollectionMethod(Map<String, String> tags, Map<String, String> metadata) {
        String explicit = pick(tags, TAG_COLLECTION_METHOD);
        if (explicit != null) return lower(explicit, "manual");
        String channel = pick(tags, "ingest.channel");
        if (channel != null) {
            return channel.toLowerCase(Locale.ROOT).contains("bulk") ? "bulk" : lower(channel, "manual");
        }
        if (pick(metadata, "collector") != null || pick(tags, "agent") != null) {
            return "scheduled";
        }
        return "manual";
    }

    private static String pick(Map<String, String> m, String key) {
        if (m == null) return null;
        String v = m.get(key);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String slug(String v, String fallback) {
        if (v == null || v.isBlank()) return fallback;
        return v.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]+", "-");
    }

    private static String token(String v, String fallback) {
        if (v == null || v.isBlank()) return fallback;
        return v.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9._-]+", "-");
    }

    private static String lower(String v, String fallback) {
        if (v == null || v.isBlank()) return fallback;
        return v.trim().toLowerCase(Locale.ROOT);
    }
}
