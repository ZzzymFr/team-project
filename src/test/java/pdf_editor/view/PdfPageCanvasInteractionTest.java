package pdf_editor.view;

import org.junit.jupiter.api.Test;
import pdf_editor.MathLibrary;
import pdf_editor.adapter.PdfEditorController;
import pdf_editor.use_case.AutoLayoutInteractor;
import pdf_editor.use_case.DocumentEditor;
import pdf_editor.use_case.PdfExportInteractor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import static org.junit.jupiter.api.Assertions.*;

/** Real Swing events against the scaled A4 page, with independent output gateways. */
class PdfPageCanvasInteractionTest {
    private final MathLibrary library = new MathLibrary();
    private final DocumentEditor document = new DocumentEditor();
    private final PdfEditorController controller = new PdfEditorController(library, document,
            new AutoLayoutInteractor(document), new PdfExportInteractor((d, p) -> {}, (d, dpi) -> java.util.List.of()));
    private final PdfPageCanvas canvas = new PdfPageCanvas(controller, new PdfPageCanvas.Listener() {
        public void contextMenu(MouseEvent event) { }
        public void editNote() { }
        public void editFormula() { }
    });

    private void fire(int type, int x, int y, int button) {
        int mask = button == MouseEvent.BUTTON1 ? MouseEvent.BUTTON1_DOWN_MASK : 0;
        canvas.dispatchEvent(new MouseEvent(canvas, type, System.currentTimeMillis(), mask,
                x, y, 1, false, button));
    }
    @Test void zoomChangesPreferredSizeNotPageDimensions() {
        int width = canvas.getPreferredSize().width;
        canvas.setZoom(2.0);
        assertTrue(canvas.getPreferredSize().width > width);
        assertEquals(595f, pdf_editor.domain.PdfDocument.PAGE_WIDTH, .001);
    }
    @Test void selectingAt200PercentFindsTheCorrectMaterial() {
        var item = controller.place(library.addFormula("x"), 100, 100, 180, 70);
        controller.clearSelection(); canvas.setZoom(2.0);
        fire(MouseEvent.MOUSE_PRESSED, 28 + 240, 28 + 220, MouseEvent.BUTTON1);
        assertEquals(item.id(), controller.selected().id());
    }
    @Test void clickingOutsidePageClearsSelectionAtAnyZoom() {
        controller.place(library.addFormula("x"), 100, 100, 180, 70);
        canvas.setZoom(3.0);
        fire(MouseEvent.MOUSE_PRESSED, 0, 0, MouseEvent.BUTTON1);
        assertNull(controller.selected());
    }
    @Test void draggingAtDoubleZoomMovesHalfAsManyDocumentPoints() {
        var item = controller.place(library.addFormula("x"), 100, 100, 180, 70);
        canvas.setZoom(2.0);
        int x = 28 + 240, y = 28 + 220;
        fire(MouseEvent.MOUSE_PRESSED, x, y, MouseEvent.BUTTON1);
        fire(MouseEvent.MOUSE_DRAGGED, x + 40, y + 30, MouseEvent.BUTTON1);
        fire(MouseEvent.MOUSE_RELEASED, x + 40, y + 30, MouseEvent.BUTTON1);
        assertEquals(120, document.find(0, item.id()).x(), .001);
        assertEquals(115, document.find(0, item.id()).y(), .001);
    }
    @Test void dragGestureCanBeUndoneOnce() {
        var item = controller.place(library.addFormula("x"), 100, 100, 180, 70);
        canvas.setZoom(2.0);
        int x = 28 + 240, y = 28 + 220;
        fire(MouseEvent.MOUSE_PRESSED, x, y, MouseEvent.BUTTON1);
        fire(MouseEvent.MOUSE_DRAGGED, x + 40, y + 30, MouseEvent.BUTTON1);
        fire(MouseEvent.MOUSE_RELEASED, x + 40, y + 30, MouseEvent.BUTTON1);
        assertTrue(controller.undo());
        assertEquals(100, document.find(0, item.id()).x(), .001);
    }
    @Test void existingZoomSurvivesPageSwitch() {
        canvas.setZoom(1.6);
        controller.addPage(); controller.switchPage(0);
        assertEquals(1.6, canvas.zoom(), .001);
    }
}
