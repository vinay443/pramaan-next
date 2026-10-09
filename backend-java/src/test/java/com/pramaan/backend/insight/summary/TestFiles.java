package com.pramaan.backend.insight.summary;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/** Builds DOCX / XLSX / PPTX / PDF / image test files in memory — no binary blobs are committed. */
public final class TestFiles {

    static final String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    static final String SS_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    static final String REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    private TestFiles() {}

    public static byte[] zip(Map<String, byte[]> entries) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream z = new ZipOutputStream(out)) {
                for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                    z.putNextEntry(new ZipEntry(e.getKey()));
                    z.write(e.getValue());
                    z.closeEntry();
                }
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static String coreXml(String title, String creator, String lastModifiedBy, String created, String modified) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><cp:coreProperties "
                + "xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" "
                + "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\">"
                + (title == null ? "" : "<dc:title>" + esc(title) + "</dc:title>")
                + (creator == null ? "" : "<dc:creator>" + esc(creator) + "</dc:creator>")
                + (lastModifiedBy == null ? "" : "<cp:lastModifiedBy>" + esc(lastModifiedBy) + "</cp:lastModifiedBy>")
                + (created == null ? "" : "<dcterms:created>" + created + "</dcterms:created>")
                + (modified == null ? "" : "<dcterms:modified>" + modified + "</dcterms:modified>")
                + "</cp:coreProperties>";
    }

    /** Paragraph strings: "H1:text" (Heading1), "H2:text", "TABLE" (an empty table), otherwise a normal paragraph. */
    public static String documentXml(String... paragraphs) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document xmlns:w=\"" + W_NS
                + "\"><w:body>");
        for (String p : paragraphs) {
            if (p.equals("TABLE")) {
                sb.append("<w:tbl><w:tr><w:tc><w:p><w:r><w:t>cell</w:t></w:r></w:p></w:tc></w:tr></w:tbl>");
            } else if (p.matches("H[1-3]:.*")) {
                sb.append("<w:p><w:pPr><w:pStyle w:val=\"Heading").append(p.charAt(1)).append("\"/></w:pPr><w:r><w:t>")
                        .append(esc(p.substring(3))).append("</w:t></w:r></w:p>");
            } else {
                sb.append("<w:p><w:r><w:t>").append(esc(p)).append("</w:t></w:r></w:p>");
            }
        }
        return sb.append("</w:body></w:document>").toString();
    }

    public static byte[] docx(String coreXml, String appXml, String documentXml) {
        Map<String, byte[]> m = new LinkedHashMap<>();
        m.put("[Content_Types].xml", b("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>"));
        m.put("word/document.xml", b(documentXml));
        if (coreXml != null) m.put("docProps/core.xml", b(coreXml));
        if (appXml != null) m.put("docProps/app.xml", b(appXml));
        return zip(m);
    }

    public static String appXml(String... nameValue) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\"?><Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\">");
        for (int i = 0; i + 1 < nameValue.length; i += 2) {
            sb.append('<').append(nameValue[i]).append('>').append(nameValue[i + 1]).append("</").append(nameValue[i]).append('>');
        }
        return sb.append("</Properties>").toString();
    }

    /** Two-sheet workbook: "Summary" (header via shared strings, 3 rows, dimension A1:C3) and "Detail" (inline strings, 2 rows). */
    public static byte[] xlsx(String coreXml) {
        Map<String, byte[]> m = new LinkedHashMap<>();
        m.put("[Content_Types].xml", b("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>"));
        m.put("xl/workbook.xml", b("<workbook xmlns=\"" + SS_NS + "\" xmlns:r=\"" + REL_NS + "\"><sheets>"
                + "<sheet name=\"Summary\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"Detail\" sheetId=\"2\" r:id=\"rId2\"/>"
                + "</sheets></workbook>"));
        m.put("xl/_rels/workbook.xml.rels", b("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Target=\"worksheets/sheet1.xml\"/>"
                + "<Relationship Id=\"rId2\" Target=\"worksheets/sheet2.xml\"/></Relationships>"));
        m.put("xl/sharedStrings.xml", b("<sst xmlns=\"" + SS_NS + "\"><si><t>Control ID</t></si><si><t>Control Name</t></si>"
                + "<si><r><t>Descrip</t></r><r><t>tion</t></r></si></sst>"));
        m.put("xl/worksheets/sheet1.xml", b("<worksheet xmlns=\"" + SS_NS + "\"><dimension ref=\"A1:C3\"/><sheetData>"
                + "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c><c r=\"C1\" t=\"s\"><v>2</v></c></row>"
                + "<row r=\"2\"><c r=\"A2\"><v>1</v></c><c r=\"B2\" t=\"inlineStr\"><is><t>Network</t></is></c></row>"
                + "<row r=\"3\"><c r=\"A3\"><v>2</v></c><c r=\"C3\" t=\"inlineStr\"><is><t>x</t></is></c></row>"
                + "</sheetData></worksheet>"));
        m.put("xl/worksheets/sheet2.xml", b("<worksheet xmlns=\"" + SS_NS + "\"><sheetData>"
                + "<row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>k</t></is></c><c r=\"D1\" t=\"inlineStr\"><is><t>v</t></is></c></row>"
                + "<row r=\"5\"><c r=\"A5\"><v>9</v></c></row></sheetData></worksheet>"));
        if (coreXml != null) m.put("docProps/core.xml", b(coreXml));
        return zip(m);
    }

    public static byte[] pptx(String coreXml, int slides) {
        Map<String, byte[]> m = new LinkedHashMap<>();
        m.put("[Content_Types].xml", b("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>"));
        m.put("ppt/presentation.xml", b("<p:presentation xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\"/>"));
        for (int i = 1; i <= slides; i++) {
            m.put("ppt/slides/slide" + i + ".xml", b("<p:sld xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\"/>"));
        }
        m.put("ppt/slideLayouts/slideLayout1.xml", b("<x/>"));
        if (coreXml != null) m.put("docProps/core.xml", b(coreXml));
        return zip(m);
    }

    // ---- PDF ---------------------------------------------------------------------------------

    /** One PDF page per element of {@code pages}; each page's lines are separated by '\n'. */
    public static byte[] pdf(String title, String author, String modifiedIso, String[] pages, String userPassword,
                             String ownerPassword, float fontSize) {
        try (PDDocument doc = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String text : pages) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(font, fontSize);
                    cs.setLeading(fontSize * 1.05f);
                    cs.newLineAtOffset(2, page.getMediaBox().getHeight() - 5);
                    for (String line : text.split("\n")) {
                        cs.showText(line);
                        cs.newLine();
                    }
                    cs.endText();
                }
            }
            PDDocumentInformation info = new PDDocumentInformation();
            if (title != null) info.setTitle(title);
            if (author != null) info.setAuthor(author);
            info.setProducer("summary-test");
            Calendar created = calendar(2026, Calendar.AUGUST, 1);
            info.setCreationDate(created);
            if (modifiedIso != null) {
                String[] p = modifiedIso.split("-");
                info.setModificationDate(calendar(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2])));
            }
            doc.setDocumentInformation(info);
            if (userPassword != null || ownerPassword != null) {
                StandardProtectionPolicy policy = new StandardProtectionPolicy(
                        ownerPassword == null ? "owner-secret" : ownerPassword, userPassword == null ? "" : userPassword,
                        new AccessPermission());
                policy.setEncryptionKeyLength(128);
                doc.protect(policy);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] pdf(String title, String author, String modifiedIso, String... pages) {
        return pdf(title, author, modifiedIso, pages, null, null, 12);
    }

    private static Calendar calendar(int y, int m, int d) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(y, m, d, 10, 0, 0);
        return c;
    }

    // ---- images --------------------------------------------------------------------------------

    public static byte[] image(int w, int h, String format) {
        try {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.setUseCache(false);
            ImageIO.write(img, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
