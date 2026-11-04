package pdf_editor.use_case;

import org.junit.jupiter.api.Test;
import pdf_editor.domain.PdfDocument;
import pdf_editor.domain.PdfPreviewPage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PdfExportInteractorTest {
    private static final class FakeOutput implements PdfExportGateway, PdfPreviewGateway {
        int exports, previews;
        @Override public void export(PdfDocument document, Path destination) { exports++; document.addPage(); }
        @Override public List<PdfPreviewPage> preview(PdfDocument document, int dpi) {
            previews++;
            assertEquals(90, dpi);
            return List.of(new PdfPreviewPage(new byte[]{1,2,3}));
        }
    }
    @Test void gatewayIsInjectedAndReceivesDefensiveSnapshot() throws IOException {
        FakeOutput fake = new FakeOutput();
        PdfExportInteractor useCase = new PdfExportInteractor(fake, fake);
        PdfDocument doc = new PdfDocument();
        useCase.export(doc, Path.of("output.pdf"));
        assertEquals(1, doc.pageCount());
        assertEquals(1, fake.exports);
        assertEquals(1, useCase.preview(doc).size());
        assertEquals(1, fake.previews);
    }
}
