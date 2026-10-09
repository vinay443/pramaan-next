package com.pramaan.backend.insight.summary;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * What the (possibly truncated) stored bytes really are, decided from magic bytes and
 * structure. The declared content type and file name are kept only so the engine can
 * report a disagreement; they never decide the format.
 */
public final class ContentProbe {

    public enum Format { EMPTY, JSON, TEXT, PDF, DOCX, XLSX, PPTX, ZIP, PNG, JPEG, GIF, OLE, GZIP, BINARY }

    /** Coarse family used to compare a declared type/extension with the detected format. */
    public enum Family { JSON, TEXT, PDF, OFFICE, ARCHIVE, IMAGE, OTHER }

    private static final int SNIFF_BYTES = 8192;

    private final byte[] bytes;
    private final boolean truncated;
    private final long totalSize;
    private final String declaredContentType;
    private final String fileName;
    private final Format format;
    private final JsonNode json;
    private final boolean jsonInvalid;
    private final String text;

    private ContentProbe(byte[] bytes, boolean truncated, long totalSize, String declaredContentType,
                         String fileName, Format format, JsonNode json, boolean jsonInvalid, String text) {
        this.bytes = bytes;
        this.truncated = truncated;
        this.totalSize = totalSize;
        this.declaredContentType = declaredContentType;
        this.fileName = fileName;
        this.format = format;
        this.json = json;
        this.jsonInvalid = jsonInvalid;
        this.text = text;
    }

    public static ContentProbe of(byte[] bytes, boolean truncated, long totalSize, String declaredContentType,
                                  String fileName, ObjectMapper mapper) {
        if (bytes.length == 0) {
            return new ContentProbe(bytes, truncated, totalSize, declaredContentType, fileName,
                    Format.EMPTY, null, false, "");
        }
        Format binary = magic(bytes);
        if (binary != null || !looksLikeText(bytes)) {
            return new ContentProbe(bytes, truncated, totalSize, declaredContentType, fileName,
                    binary == null ? Format.BINARY : binary, null, false, null);
        }
        String decoded = decode(bytes);
        JsonNode json = null;
        boolean invalid = false;
        if (startsLikeJson(decoded)) {
            try {
                JsonNode n = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                        .readTree(decoded);
                if (n != null && n.isContainerNode()) {
                    json = n;
                } else {
                    invalid = true;
                }
            } catch (Exception e) {
                invalid = true;
            }
        }
        return new ContentProbe(bytes, truncated, totalSize, declaredContentType, fileName,
                json != null ? Format.JSON : Format.TEXT, json, invalid, decoded);
    }

    private static String decode(byte[] bytes) {
        String s = new String(bytes, StandardCharsets.UTF_8); // malformed input becomes U+FFFD, never throws
        return s.startsWith("﻿") ? s.substring(1) : s;
    }

    private static boolean startsLikeJson(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isWhitespace(c)) {
                return c == '{' || c == '[';
            }
        }
        return false;
    }

    private static Format magic(byte[] b) {
        if (startsWith(b, '%', 'P', 'D', 'F', '-')) return Format.PDF;
        if (startsWith(b, 0x50, 0x4B, 0x03, 0x04)) {
            return classifyZip(b);
        }
        if (startsWith(b, 0x89, 'P', 'N', 'G')) return Format.PNG;
        if (startsWith(b, 0xFF, 0xD8, 0xFF)) return Format.JPEG;
        if (startsWith(b, 'G', 'I', 'F', '8')) return Format.GIF;
        if (startsWith(b, 0xD0, 0xCF, 0x11, 0xE0)) return Format.OLE;
        if (startsWith(b, 0x1F, 0x8B)) return Format.GZIP;
        if (startsWith(b, 0x7F, 'E', 'L', 'F')) return Format.BINARY;
        return null;
    }

    /**
     * Zip container: classify by the entry names (streamed, never written anywhere). A truncated zip
     * (only a prefix was read) falls back to looking for the part names in the first 64 KB.
     */
    private static Format classifyZip(byte[] b) {
        java.util.Set<String> names = new java.util.HashSet<>();
        try (java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(b))) {
            java.util.zip.ZipEntry e;
            while ((e = zin.getNextEntry()) != null && names.size() < 2000) {
                names.add(e.getName());
                if (names.contains("word/document.xml") || names.contains("xl/workbook.xml")
                        || names.contains("ppt/presentation.xml")) {
                    break;
                }
            }
        } catch (java.io.IOException | RuntimeException ex) {
            // truncated or damaged: use whatever names were seen, else the raw header text
        }
        if (names.isEmpty()) {
            String head = new String(b, 0, Math.min(b.length, 65536), StandardCharsets.ISO_8859_1);
            if (head.contains("word/document.xml") || head.contains("word/")) names.add("word/document.xml");
            else if (head.contains("xl/workbook.xml") || head.contains("xl/")) names.add("xl/workbook.xml");
            else if (head.contains("ppt/presentation.xml") || head.contains("ppt/")) names.add("ppt/presentation.xml");
        }
        if (names.contains("word/document.xml")) return Format.DOCX;
        if (names.contains("xl/workbook.xml")) return Format.XLSX;
        if (names.contains("ppt/presentation.xml")) return Format.PPTX;
        return Format.ZIP;
    }

    /** True when the bytes start like a PDF or a zip container — the formats worth reading beyond 1 MiB. */
    public static boolean isDocumentContainer(byte[] b) {
        return startsWith(b, '%', 'P', 'D', 'F', '-') || startsWith(b, 0x50, 0x4B, 0x03, 0x04);
    }

    private static boolean startsWith(byte[] b, int... prefix) {
        if (b.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if ((b[i] & 0xFF) != prefix[i]) return false;
        }
        return true;
    }

    private static boolean looksLikeText(byte[] b) {
        int n = Math.min(b.length, SNIFF_BYTES);
        int control = 0;
        for (int i = 0; i < n; i++) {
            int v = b[i] & 0xFF;
            if (v == 0) return false;
            if (v < 0x20 && v != '\t' && v != '\n' && v != '\r' && v != '\f') control++;
        }
        if (control * 10 > n) return false;
        String sample = new String(b, 0, n, StandardCharsets.UTF_8);
        long bad = sample.chars().filter(c -> c == '�').count();
        return bad * 3 <= sample.length();
    }

    public byte[] bytes() { return bytes; }

    public boolean truncated() { return truncated; }

    /** Size of the whole stored object (may exceed {@link #bytes()}.length when truncated). */
    public long totalSize() { return totalSize; }

    public String declaredContentType() { return declaredContentType; }

    public String fileName() { return fileName; }

    public Format format() { return format; }

    /** Parsed JSON object/array, or null. */
    public JsonNode json() { return json; }

    /** True when the bytes start like JSON but do not parse. */
    public boolean jsonInvalid() { return jsonInvalid; }

    /** Decoded text (invalid UTF-8 replaced), or null for binary content. */
    public String text() { return text; }

    public String extension() {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Family of what the bytes are. */
    public Family detectedFamily() {
        return switch (format) {
            case JSON -> Family.JSON;
            case TEXT, EMPTY -> Family.TEXT;
            case PDF -> Family.PDF;
            case DOCX, XLSX, PPTX, OLE -> Family.OFFICE;
            case ZIP, GZIP -> Family.ARCHIVE;
            case PNG, JPEG, GIF -> Family.IMAGE;
            case BINARY -> Family.OTHER;
        };
    }

    /** Family claimed by the declared content type, else by the file extension; OTHER when unspecific. */
    public Family declaredFamily() {
        String ct = declaredContentType == null ? "" : declaredContentType.toLowerCase(Locale.ROOT).trim();
        int semi = ct.indexOf(';');
        if (semi >= 0) ct = ct.substring(0, semi).trim();
        Family f = familyOfMime(ct);
        if (f != Family.OTHER) return f;
        return switch (extension()) {
            case "json" -> Family.JSON;
            case "txt", "log", "md", "markdown", "csv", "tsv", "xml", "yml", "yaml", "html", "htm", "conf",
                 "cfg" -> Family.TEXT;
            case "pdf" -> Family.PDF;
            case "docx", "xlsx", "pptx", "doc", "xls" -> Family.OFFICE;
            case "zip", "gz" -> Family.ARCHIVE;
            case "png", "jpg", "jpeg", "gif" -> Family.IMAGE;
            default -> Family.OTHER;
        };
    }

    private static Family familyOfMime(String ct) {
        if (ct.isEmpty() || ct.equals("application/octet-stream")) return Family.OTHER;
        if (ct.contains("json")) return Family.JSON;
        if (ct.startsWith("text/") || ct.contains("xml") || ct.contains("csv")) return Family.TEXT;
        if (ct.equals("application/pdf")) return Family.PDF;
        if (ct.contains("officedocument") || ct.contains("msword") || ct.contains("ms-excel")) return Family.OFFICE;
        if (ct.contains("zip") || ct.contains("gzip")) return Family.ARCHIVE;
        if (ct.startsWith("image/")) return Family.IMAGE;
        return Family.OTHER;
    }

    /** True when declared and detected families are both specific and incompatible (JSON is text). */
    public boolean declaredDisagrees() {
        Family declared = declaredFamily();
        if (declared == Family.OTHER) return false;
        Family detected = detectedFamily();
        if (declared == detected) return false;
        return !(declared == Family.TEXT && detected == Family.JSON);
    }

    /** Human label of the detected format, e.g. "PDF document". */
    public String detectedLabel() {
        return switch (format) {
            case EMPTY -> "empty content";
            case JSON -> "JSON";
            case TEXT -> "plain text";
            case PDF -> "PDF document";
            case DOCX -> "Word (DOCX) document";
            case XLSX -> "Excel (XLSX) workbook";
            case PPTX -> "PowerPoint (PPTX) presentation";
            case ZIP -> "ZIP archive";
            case PNG -> "PNG image";
            case JPEG -> "JPEG image";
            case GIF -> "GIF image";
            case OLE -> "legacy Office (OLE) document";
            case GZIP -> "gzip archive";
            case BINARY -> "binary data";
        };
    }
}
