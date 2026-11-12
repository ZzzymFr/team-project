package pdf_editor.data_access;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import pdf_editor.MathLibrary;
import pdf_editor.domain.PdfDocument;
import pdf_editor.use_case.DocumentEditor;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Real PDFBox round-trip tests, not a fake export port. */
class PdfBoxOutputAdapterIntegrationTest {
    @TempDir Path temp;
    private final PdfBoxOutputAdapter adapter = new PdfBoxOutputAdapter();

    @Test void emptyDocumentExportsOneA4Page() throws Exception {
        Path file = temp.resolve("empty.pdf");
        adapter.export(new PdfDocument(), file);
        try (PDDocument pdf = PDDocument.load(file.toFile())) {
            assertEquals(1, pdf.getNumberOfPages());
            assertEquals(PDRectangle.A4.getWidth(), pdf.getPage(0).getMediaBox().getWidth());
            assertEquals(PDRectangle.A4.getHeight(), pdf.getPage(0).getMediaBox().getHeight());
        }
    }
    @Test void formulaExportKeepsCopyableLatexAppendix() throws Exception {
        var editor = new DocumentEditor();
        editor.place(0, new MathLibrary().addCalculation("x^2+1=", "=2"), 45, 60, 300, 110);
        Path file = temp.resolve("formula.pdf");
        adapter.export(editor.snapshot(), file);
        try (PDDocument pdf = PDDocument.load(file.toFile())) {
            assertEquals(2, pdf.getNumberOfPages());
            String text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("Appendix"));
            assertTrue(text.contains("x^2+1="));
            assertTrue(text.contains("Result: =2"));
            var images = pdf.getPage(0).getResources().getXObjectNames().iterator();
            assertTrue(images.hasNext(), "Formula page must actually contain embedded artwork");
            var embedded = pdf.getPage(0).getResources().getXObject(images.next());
            assertTrue(embedded instanceof PDImageXObject);
            assertEquals(1200, ((PDImageXObject) embedded).getWidth(),
                    "300 PDF points should produce 1200 source pixels at 4x export quality");
        }
    }
    @Test void graphAndNoteExportDoNotCreateLatexAppendix() throws Exception {
        var graphPixels = new BufferedImage(100, 60, BufferedImage.TYPE_INT_RGB);
        for (int y=0; y<60; y++) for (int x=0; x<100; x++) graphPixels.setRGB(x,y,Color.BLUE.getRGB());
        var editor = new DocumentEditor();
        editor.place(0, MathLibrary.region("plot", graphPixels), 20, 20, 300, 200);
        editor.addNote(0, "Note content", 20, 280);
        Path file = temp.resolve("graph.pdf");
        adapter.export(editor.snapshot(), file);
        try (PDDocument pdf = PDDocument.load(file.toFile())) {
            assertEquals(1, pdf.getNumberOfPages());
            assertTrue(pdf.getPage(0).getResources().getXObjectNames().iterator().hasNext());
        }
    }
    @Test void multiPageAndPreviewPreserveDocumentState() throws Exception {
        var editor = new DocumentEditor();
        editor.place(0, new MathLibrary().addFormula("a+b"), 20, 20, 200, 80);
        editor.addPage();
        editor.addNote(1, "Second page", 20, 20);
        var snapshot = editor.snapshot();
        var previews = adapter.preview(snapshot, 72);
        assertEquals(3, previews.size(), "Two content pages plus LaTeX appendix");
        assertEquals(2, snapshot.pageCount());
        for (var preview : previews) assertTrue(preview.png().length > 100);
    }
    @Test void invalidPreviewDpiIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> adapter.preview(new PdfDocument(), 0));
        assertThrows(IllegalArgumentException.class, () -> adapter.preview(new PdfDocument(), -1));
    }
    @Test void duplicateMaterialOnSeveralPagesHasSingleAppendixEntry() throws Exception {
        var source = new MathLibrary().addFormula("z^3");
        var editor = new DocumentEditor();
        editor.place(0, source, 40, 40, 200, 100);
        editor.addPage();
        editor.place(1, source, 40, 40, 200, 100);
        Path file = temp.resolve("duplicate.pdf");
        adapter.export(editor.snapshot(), file);
        try (PDDocument pdf = PDDocument.load(file.toFile())) {
            assertEquals(3, pdf.getNumberOfPages());
            String appendix = new PDFTextStripper().getText(pdf);
            // String.split takes a REGEX: a raw ^ is an anchor, not the
            // literal caret in z^3. Count the exact string instead.
            assertEquals(1, appendix.split(java.util.regex.Pattern.quote("Formula: z^3"), -1).length - 1);
        }
    }
}
