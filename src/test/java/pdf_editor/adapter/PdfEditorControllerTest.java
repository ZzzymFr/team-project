package pdf_editor.adapter;

import org.junit.jupiter.api.Test;
import pdf_editor.MathLibrary;
import pdf_editor.domain.PdfDocument;
import pdf_editor.domain.PdfPreviewPage;
import pdf_editor.use_case.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PdfEditorControllerTest {
    private PdfEditorController newController(MathLibrary library) {
        DocumentEditor e = new DocumentEditor();
        PdfExportGateway exportGateway = (document, path) -> {};
        PdfPreviewGateway previewGateway = (document, dpi) -> List.of();
        return new PdfEditorController(library, e, new AutoLayoutInteractor(e),
                new PdfExportInteractor(exportGateway, previewGateway));
    }
    @Test void selectionAndPageMovesUseUseCases() {
        MathLibrary library = new MathLibrary();
        var controller = newController(library);
        var p = controller.place(library.addCalculation("1+1", "2"), 10,20, 300,100);
        controller.selectAt(20,30);
        assertEquals(p.id(), controller.selected().id());
        assertTrue(controller.moveSelectedToPage(-1));
        assertEquals(2, controller.pageCount());
        assertEquals(1, controller.currentPage());
        assertEquals(p.id(), controller.selected().id());
        assertTrue(controller.undo());
        assertEquals(1, controller.pageCount());
        assertEquals(1, controller.placements().size());
    }
}
