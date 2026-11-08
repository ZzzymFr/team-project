package pdf_editor.use_case;

import org.junit.jupiter.api.Test;
import pdf_editor.domain.PdfDocument;
import java.io.IOException;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PdfExportInteractorTest {
    @Test void gatewayIsInjectedAndReceivesDefensiveSnapshot() throws IOException {
        int[] exports = new int[1];
        PdfExportInteractor useCase = new PdfExportInteractor((document, destination) -> {
            exports[0]++;
            document.addPage();
        });
        PdfDocument doc = new PdfDocument();
        useCase.export(doc, Path.of("output.pdf"));
        assertEquals(1, doc.pageCount());
        assertEquals(1, exports[0]);
    }
}
