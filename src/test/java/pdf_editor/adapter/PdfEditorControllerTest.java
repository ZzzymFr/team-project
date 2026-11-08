package pdf_editor.adapter;

import org.junit.jupiter.api.Test;
import pdf_editor.MathLibrary;
import pdf_editor.domain.PdfDocument;
import pdf_editor.use_case.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PdfEditorControllerTest {
    private PdfEditorController newController(MathLibrary library) {
        DocumentEditor e = new DocumentEditor();
        PdfExportGateway exportGateway = (document, path) -> {};
        return new PdfEditorController(library, e, new AutoLayoutInteractor(e),
                new PdfExportInteractor(exportGateway));
    }
    @Test void selectionAndPageMovesUseUseCases() {
        MathLibrary library = new MathLibrary();
        var controller = newController(library);
        var p = controller.place(library.addCalculation("1+1", "2"), 10,20, 300,100);
        controller.select(p.id());
        assertEquals(p.id(), controller.selected().id());
        assertTrue(controller.moveSelectedToPage(-1));
        assertEquals(2, controller.pageCount());
        assertEquals(1, controller.currentPage());
        assertEquals(p.id(), controller.selected().id());
        assertEquals(1, controller.placements().size());
    }

    @Test void restoreBringsBackASnapshotAndForgetsTheSelection() {
        MathLibrary library = new MathLibrary();
        var controller = newController(library);
        PdfDocument before = controller.documentSnapshot();
        var p = controller.place(library.addFormula("x"), 10, 20, 300, 100);
        controller.select(p.id());
        controller.restore(before);
        assertTrue(controller.placements().isEmpty());
        assertNull(controller.selected());
    }
}
