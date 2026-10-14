package com.pramaan.backend.evidence;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Use Case 3 — the single naming convention and metadata-tag set applied to every
 * evidence record, whether scheduler-collected, bulk-uploaded, or produced by an
 * on-demand predefined query.
 *
 * <p>Name: {@code {sourceApp}_{controlCode}_{evidenceType}[_{ENV}]_{yyyyMMdd}_{seq}}
 * (e.g. {@code payments_DB-TLS-IN-TRANSIT_DB-CONFIG_UAT_20260908_001}). {@code seq} is a
 * zero-padded per-record sequence, unique among records sharing the rest of the name and
 * stable across that record's new versions (the version lives in the {@code version} tag).
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

    /** Tag keys every record is expected to carry (the set {@link #standardTags} always writes; sourceTitle is optional). */
    public static final List<String> EXPECTED_TAG_KEYS = List.of(TAG_APPLICATION, TAG_TECHNOLOGY, TAG_CONTROL,
            TAG_FRAMEWORK, TAG_FRAMEWORKS, TAG_EVIDENCE_TYPE, TAG_NAME, TAG_COLLECTION_METHOD, TAG_VERSION);

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private EvidenceNaming() {}

    /** The canonical evidence name without an environment segment. */
    public static String standardName(String application, String controlCode, String evidenceType,
                                      Instant collectedAt, int seq) {
        return standardName(application, controlCode, evidenceType, null, collectedAt, seq);
    }

    /**
     * The canonical evidence name {@code app_control_TYPE[_ENV]_yyyymmdd_NNN}. The environment segment is
     * omitted when blank. {@code seq} is the per-record sequence (see {@link #resolveSequence}).
     */
    public static String standardName(String application, String controlCode, String evidenceType,
                                      String environment, Instant collectedAt, int seq) {
        return namePrefix(application, controlCode, evidenceType, environment, collectedAt)
                + "_" + String.format(Locale.ROOT, "%03d", Math.max(seq, 1));
    }

    /** Everything in the name before the trailing {@code _NNN}. */
    public static String namePrefix(String application, String controlCode, String evidenceType,
                                    String environment, Instant collectedAt) {
        List<String> parts = new ArrayList<>();
        parts.add(slug(application, "app"));
        parts.add(token(controlCode, "CONTROL"));
        parts.add(token(evidenceType, "GENERAL"));
        if (environment != null && !environment.isBlank()) {
            parts.add(token(environment, "ENV"));
        }
        parts.add(STAMP.format(collectedAt == null ? Instant.EPOCH : collectedAt));
        return String.join("_", parts);
    }

    /**
     * Sequence for a record's name under {@code prefix}. A record keeps the sequence already in its current
     * title when that title shares the prefix (so new versions keep their identity); otherwise it takes the
     * next number after every other record's title under the same prefix.
     */
    public static int resolveSequence(String prefix, String currentTitle, Collection<String> otherTitles) {
        int own = sequenceOf(prefix, currentTitle);
        if (own > 0) return own;
        int max = 0;
        if (otherTitles != null) {
            for (String t : otherTitles) max = Math.max(max, sequenceOf(prefix, t));
        }
        return max + 1;
    }

    /**
     * Sequence for a record whose prefix changed through a metadata edit: keeps the NNN of its current title when no
     * other record already uses {@code prefix_NNN}, otherwise takes the next number after every other title under the
     * new prefix. Never returns a sequence another record holds.
     */
    public static int resolveEditedSequence(String prefix, String currentTitle, Collection<String> otherTitles) {
        int own = sequenceOf(prefix, currentTitle);
        int wanted = own > 0 ? own : trailingSequence(currentTitle);
        int max = 0;
        boolean taken = false;
        if (otherTitles != null) {
            for (String t : otherTitles) {
                int s = sequenceOf(prefix, t);
                max = Math.max(max, s);
                if (s == wanted && s > 0) taken = true;
            }
        }
        return wanted > 0 && !taken ? wanted : max + 1;
    }

    /** The trailing {@code _NNN} of any evidence name, else 0. */
    static int trailingSequence(String title) {
        if (title == null) return 0;
        int us = title.lastIndexOf('_');
        if (us < 0) return 0;
        String tail = title.substring(us + 1);
        if (tail.length() < 3 || !tail.chars().allMatch(Character::isDigit)) return 0;
        try {
            return Integer.parseInt(tail);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** SQL LIKE pattern (escape char {@code !}) matching every title that starts with {@code prefix_}. */
    public static String titlePattern(String prefix) {
        return prefix.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "!_%";
    }

    /** The {@code NNN} of {@code title} when it is exactly {@code prefix_NNN}, else 0. */
    static int sequenceOf(String prefix, String title) {
        if (title == null || !title.startsWith(prefix + "_")) return 0;
        String tail = title.substring(prefix.length() + 1);
        if (tail.length() < 3 || !tail.chars().allMatch(Character::isDigit)) return 0;
        try {
            return Integer.parseInt(tail);
        } catch (NumberFormatException e) {
            return 0;
        }
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

    /** Canonical evidence-type token (upper case, sanitised), as written to the {@code evidenceType} tag and the name. */
    public static String typeToken(String v) {
        return token(v, "GENERAL");
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
