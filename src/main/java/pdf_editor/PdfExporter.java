package pdf_editor;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PdfExporter {
    public void export(PdfDocument document, Path target) throws IOException {
        try (PDDocument pdf = build(document)) { pdf.save(target.toFile()); }
    }
    public PDDocument build(PdfDocument document) throws IOException {
        PDDocument pdf = new PDDocument();
        try {
            for (java.util.List<PdfDocument.Placement> pageItems : document.pages) {
                PDPage page = new PDPage(PDRectangle.A4); pdf.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
                    for (PdfDocument.Placement p : pageItems) {
                        if (p.width < 1 || p.height < 1) continue;
                        int w = Math.min(2000, Math.max(1, Math.round(p.width * 2)));
                        int h = Math.min(2000, Math.max(1, Math.round(p.height * 2)));
                        BufferedImage image = p.isNote() ? NoteRenderer.render(p, w, h) : MaterialRenderer.render(p.material, w, h);
                        PDImageXObject xobj = LosslessFactory.createFromImage(pdf, image);
                        stream.drawImage(xobj, p.x, PdfDocument.PAGE_HEIGHT - p.y - p.height, p.width, p.height);
                    }
                }
            }
            addAppendix(pdf, document);
            return pdf;
        } catch (IOException | RuntimeException ex) { pdf.close(); throw ex; }
    }
    private void addAppendix(PDDocument pdf, PdfDocument document) throws IOException {
        Map<String, MathLibrary.Material> used = new LinkedHashMap<>();
        for (java.util.List<PdfDocument.Placement> page : document.pages)
            for (PdfDocument.Placement placement : page)
                if (!placement.isNote() && !placement.material.isGraph()) used.put(placement.material.id, placement.material);
        if (used.isEmpty()) return;
        PDPage page = null;
        PDPageContentStream stream = null;
        float y = 0;
        try {
            for (MathLibrary.Material item : used.values()) {
                if (stream == null || y < 110) {
                    if (stream != null) stream.close();
                    page = new PDPage(PDRectangle.A4); pdf.addPage(page);
                    stream = new PDPageContentStream(pdf, page);
                    y = 795;
                    text(stream, 16, 45, y, "Appendix - Copyable LaTeX Source"); y -= 40;
                }
                text(stream, 12, 45, y, item.title); y -= 20;
                y = writeWrapped(stream, item.formula, y, "Formula: ");
                if (item.result != null && !item.result.isEmpty())
                    y = writeWrapped(stream, item.result, y, "Result: ");
                y -= 15;
            }
        } finally { if (stream != null) stream.close(); }
    }
    private static float writeWrapped(PDPageContentStream stream, String source, float y, String label) throws IOException {
        // PDFBox's standard Type1 font is ASCII-oriented. Escape non-ASCII rather than dropping content.
        String escaped = ascii(source == null ? "" : source).replace("\r", " ").replace("\n", " ");
        String full = label + escaped;
        while (!full.isEmpty()) {
            int count = Math.min(88, full.length());
            text(stream, 10, 45, y, full.substring(0, count));
            full = full.substring(count); y -= 15;
        }
        return y;
    }
    private static String ascii(String text) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 32 && c <= 126) b.append(c);
            else b.append(String.format("\\u%04X", (int) c));
        }
        return b.toString();
    }
    private static void text(PDPageContentStream stream, int fontSize, float x, float y, String text) throws IOException {
        stream.beginText(); stream.setFont(PDType1Font.COURIER, fontSize);
        stream.newLineAtOffset(x, y); stream.showText(text); stream.endText();
    }
}
