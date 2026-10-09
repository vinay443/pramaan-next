package com.pramaan.backend.insight.summary;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Read-only, in-memory view of an Office Open XML (zip) package, hardened for untrusted files:
 * at most {@value #MAX_ENTRIES} entries, {@code 20 MiB} per entry, {@code 100 MiB} in total, a
 * compression-ratio ceiling of {@value #MAX_RATIO}:1 on entries over 1 MiB, no path traversal, and
 * nothing ever written to disk. XML is parsed with DTDs and external entities switched off, and any
 * part that contains a DOCTYPE is refused outright.
 */
final class OoxmlPackage {

    static final int MAX_ENTRIES = 2000;
    static final long MAX_ENTRY_BYTES = 20L * 1024 * 1024;
    static final long MAX_TOTAL_BYTES = 100L * 1024 * 1024;
    static final long MAX_KEPT_BYTES = 40L * 1024 * 1024;
    static final int MAX_RATIO = 100;
    private static final long RATIO_CHECK_FROM = 1024 * 1024;

    /** The package as a whole cannot be trusted or read. */
    static final class Rejected extends Exception {
        Rejected(String message) {
            super(message);
        }
    }

    /** One XML part was refused (DOCTYPE, unsupported encoding) — the rest of the package is still usable. */
    static final class XmlRefused extends Exception {
        XmlRefused(String message) {
            super(message);
        }
    }

    private final Map<String, byte[]> kept;
    private final List<String> names;

    private OoxmlPackage(Map<String, byte[]> kept, List<String> names) {
        this.kept = kept;
        this.names = names;
    }

    /** Streams the zip once; keeps only parts for which {@code keep} is true. */
    static OoxmlPackage read(byte[] zip, Predicate<String> keep) throws Rejected, IOException {
        Map<String, byte[]> kept = new LinkedHashMap<>();
        List<String> names = new ArrayList<>();
        long total = 0;
        long keptBytes = 0;
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            byte[] buf = new byte[16 * 1024];
            while ((e = zin.getNextEntry()) != null) {
                if (names.size() >= MAX_ENTRIES) {
                    throw new Rejected("the archive has more than " + MAX_ENTRIES + " entries");
                }
                String name = e.getName();
                if (unsafe(name)) {
                    throw new Rejected("the archive contains an unsafe entry path");
                }
                names.add(name);
                boolean wanted = !e.isDirectory() && keep.test(name);
                ByteArrayOutputStream out = wanted ? new ByteArrayOutputStream() : null;
                long size = 0;
                int n;
                while ((n = zin.read(buf)) > 0) {
                    size += n;
                    total += n;
                    if (size > MAX_ENTRY_BYTES) {
                        throw new Rejected("an entry expands to more than " + (MAX_ENTRY_BYTES >> 20) + " MB");
                    }
                    if (total > MAX_TOTAL_BYTES) {
                        throw new Rejected("the archive expands to more than " + (MAX_TOTAL_BYTES >> 20) + " MB");
                    }
                    if (out != null) {
                        out.write(buf, 0, n);
                    }
                }
                long compressed = e.getCompressedSize();
                if (size > RATIO_CHECK_FROM && compressed > 0 && size / compressed > MAX_RATIO) {
                    throw new Rejected("an entry has a compression ratio above " + MAX_RATIO + ":1");
                }
                if (out != null) {
                    keptBytes += out.size();
                    if (keptBytes > MAX_KEPT_BYTES) {
                        throw new Rejected("the parts to be read exceed " + (MAX_KEPT_BYTES >> 20) + " MB");
                    }
                    kept.put(name, out.toByteArray());
                }
            }
        }
        return new OoxmlPackage(kept, names);
    }

    private static boolean unsafe(String name) {
        return name == null || name.startsWith("/") || name.startsWith("\\") || name.contains("..")
                || name.contains("\\") || (name.length() > 1 && name.charAt(1) == ':');
    }

    List<String> names() {
        return names;
    }

    boolean has(String name) {
        return kept.containsKey(name);
    }

    /** A hardened StAX reader over a kept part; null when the part is absent. */
    XMLStreamReader xml(String name) throws XmlRefused, XMLStreamException {
        byte[] b = kept.get(name);
        if (b == null) {
            return null;
        }
        if (b.length >= 2 && ((b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF
                || (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE)) {
            throw new XmlRefused(name + " is UTF-16 encoded, which is not read");
        }
        if (containsDoctype(b)) {
            throw new XmlRefused(name + " contains a DOCTYPE declaration (possible external-entity attack); "
                    + "the part was not parsed");
        }
        return factory().createXMLStreamReader(new ByteArrayInputStream(b));
    }

    static boolean containsDoctype(byte[] b) {
        byte[] pat = "<!DOCTYPE".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i + pat.length <= b.length; i++) {
            for (int j = 0; j < pat.length; j++) {
                if (Character.toUpperCase((char) (b[i + j] & 0xFF)) != pat[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static XMLInputFactory factory() {
        XMLInputFactory f = XMLInputFactory.newFactory();
        set(f, XMLInputFactory.SUPPORT_DTD, false);
        set(f, XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        set(f, XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        set(f, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        set(f, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return f;
    }

    private static void set(XMLInputFactory f, String property, Object value) {
        try {
            f.setProperty(property, value);
        } catch (IllegalArgumentException ignored) {
            // property not supported by this StAX implementation; the DOCTYPE pre-check still applies
        }
    }
}
