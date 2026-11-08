package pdf_editor.view;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pdf_editor.MathLibrary;
import pdf_editor.adapter.PdfEditorController;
import pdf_editor.data_access.PdfBoxOutputAdapter;
import pdf_editor.domain.PdfDocument;
import pdf_editor.use_case.AutoLayoutInteractor;
import pdf_editor.use_case.DocumentEditor;
import pdf_editor.use_case.PdfExportInteractor;
import view.DrawingCanvas;
import view.Paper;
import view.canvas.SharedCanvas;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import view.ribbon.Ribbon;

/** The PDF document's items on the canvas's A4 sheets. */
class PaperItemsLayerTest {
    private static final int SECOND_SHEET = Paper.PAGE_HEIGHT + Paper.PAGE_GAP;

    private final MathLibrary library = new MathLibrary();
    private final DocumentEditor editor = new DocumentEditor();
    private final PdfBoxOutputAdapter output = new PdfBoxOutputAdapter();
    private final PdfEditorController controller = new PdfEditorController(library, editor,
            new AutoLayoutInteractor(editor), new PdfExportInteractor(output));
    private final DrawingCanvas drawing = new DrawingCanvas();
    private final SharedCanvas canvas = new SharedCanvas(drawing);
    private final PaperItemsLayer items = new PaperItemsLayer(controller);

    PaperItemsLayerTest() {
        drawing.setSize(900, 600);  // world (450, 300) is in the middle of the view
        drawing.setPaperLayer(items);
    }

    private void drag(int... xy) {
        send(MouseEvent.MOUSE_PRESSED, xy[0], xy[1], 1);
        for (int i = 2; i < xy.length; i += 2) {
            send(MouseEvent.MOUSE_DRAGGED, xy[i], xy[i + 1], 1);
        }
        send(MouseEvent.MOUSE_RELEASED, xy[xy.length - 2], xy[xy.length - 1], 1);
    }

    private void send(int id, int x, int y, int clicks) {
        drawing.dispatchEvent(new MouseEvent(drawing, id, 0, InputEvent.BUTTON1_DOWN_MASK, x, y, clicks, false,
                MouseEvent.BUTTON1));
    }

    private List<PdfDocument.Placement> on(int page) {
        PdfDocument document = controller.documentSnapshot();
        return page < document.pageCount() ? document.placements(page) : List.of();
    }

    private Rectangle2D boxOf(int page) {
        return PaperItemsLayer.worldBox(page, on(page).get(0));
    }

    private PdfDocument.Placement insertCalculation() {
        items.insert(library.addCalculation("1+1", "2"));
        return on(0).get(0);
    }

    @Test
    void insertingPutsTheItemInTheMiddleOfTheViewSelectedWithTheLasso() {
        insertCalculation();
        Rectangle2D box = boxOf(0);
        assertEquals(450, box.getCenterX(), 1);
        assertEquals(300, box.getCenterY(), 1);
        assertTrue(items.hasSelection());
        assertEquals(DrawingCanvas.Tool.LASSO, drawing.getTool());
        assertEquals(1, items.pagesUsed());
        assertEquals(2, drawing.getPageCount(), "a blank sheet after the one with the item");
    }

    @Test
    void theLassoMovesAnItemAndUndoPutsItBack() {
        float y = insertCalculation().y();
        drawing.clearSelection();
        assertFalse(items.hasSelection());

        drag(300, 300, 300, 350, 300, 400);
        assertTrue(items.hasSelection(), "clicked: selected");
        assertEquals(y + 100 / PaperItemsLayer.UNIT, on(0).get(0).y(), 0.5);

        drawing.undo();
        assertEquals(y, on(0).get(0).y(), 0.01);
        drawing.undo();
        assertTrue(on(0).isEmpty(), "the same Undo steps back through the insert");
        assertFalse(drawing.canUndo());
    }

    @Test
    void anItemCanBeDraggedOntoTheNextSheet() {
        insertCalculation();
        drag(300, 300, 300, SECOND_SHEET + 300);
        assertTrue(on(0).isEmpty());
        assertEquals(1, on(1).size());
        assertEquals(SECOND_SHEET + 233, boxOf(1).getY(), 2);
        assertEquals(3, drawing.getPageCount());
    }

    @Test
    void theCornerResizesTheItem() {
        PdfDocument.Placement before = insertCalculation();
        Rectangle2D box = boxOf(0);
        int x = (int) box.getMaxX() - 2;
        int y = (int) box.getMaxY() - 2;
        drag(x, y, x + 40, y + 40);
        assertEquals(before.width() + 40 / PaperItemsLayer.UNIT, on(0).get(0).width(), 0.5);
        assertEquals(before.x(), on(0).get(0).x(), 0.01, "the top-left corner stays");
    }

    @Test
    void thePenWritesOverItemsInsteadOfMovingThem() {
        float y = insertCalculation().y();
        drawing.setTool(DrawingCanvas.Tool.PEN);
        drag(300, 300, 340, 320);
        assertEquals(1, drawing.getStrokes().size());
        assertEquals(y, on(0).get(0).y(), 0.01);
    }

    @Test
    void deleteRemovesTheSelectedItemAsOneUndoStep() {
        insertCalculation();
        assertTrue(drawing.hasSelection());
        drawing.deleteSelection();
        assertTrue(on(0).isEmpty());
        drawing.undo();
        assertEquals(1, on(0).size());
    }

    @Test
    void droppingALibraryItemPlacesItWhereItWasDropped() {
        MathLibrary.Material calculation = library.addCalculation("2+2", "4");
        assertTrue(items.drop(calculation.id(), new java.awt.geom.Point2D.Double(400, 500)));
        assertEquals(500, boxOf(0).getCenterY(), 1);
        assertTrue(items.drop(calculation.id(), new java.awt.geom.Point2D.Double(400, SECOND_SHEET + 500)),
                "the blank sheet that follows takes drops too");
        assertEquals(1, on(1).size());
        assertEquals(SECOND_SHEET + 500, boxOf(1).getCenterY(), 1);
        assertFalse(items.drop("no such id", new java.awt.geom.Point2D.Double(400, 400)));
    }

    @Test
    void theEditTabEnablesItemCommandsOnlyWithASelection() {
        LibraryPanel panel = new LibraryPanel(library, items::insert, () -> { });
        panel.setVisible(false);
        Ribbon bar = new Ribbon("Math Lab");
        DocumentRibbon ribbon = new DocumentRibbon(bar, canvas, controller, items, panel);
        assertTrue(ribbon.editTab().isSelected(), "the first tab starts chosen");
        bar.refresh();
        assertFalse(ribbon.frontButton().isEnabled());
        assertFalse(ribbon.layoutButton().isEnabled(), "nothing in the library yet");

        insertCalculation();
        bar.refresh();
        assertTrue(ribbon.frontButton().isEnabled());
        assertTrue(ribbon.splitButton().isEnabled(), "a calculation with its result");
        assertTrue(ribbon.layoutButton().isEnabled());

        ribbon.libraryButton().doClick();
        assertTrue(panel.isVisible());
        assertEquals(1, panel.itemCount());

        ribbon.splitButton().doClick();
        assertEquals(2, on(0).size(), "formula and result side by side");
        ribbon.deleteButton().doClick();
        assertEquals(1, on(0).size());
    }

    @Test
    void saveImportAndExportSitInTheTitleRow() {
        LibraryPanel panel = new LibraryPanel(library, items::insert, () -> { });
        Ribbon bar = new Ribbon("Math Lab");
        DocumentRibbon ribbon = new DocumentRibbon(bar, canvas, controller, items, panel);
        assertEquals("Save", ribbon.saveButton().getToolTipText());
        assertEquals("", ribbon.saveButton().getText(), "Save is just an icon");
        assertEquals("Import", ribbon.importButton().getToolTipText());
        assertEquals("", ribbon.importButton().getText(), "Import is just an icon");
        assertEquals("Export PDF", ribbon.exportButton().getToolTipText());
        assertEquals("", ribbon.exportButton().getText(), "Export PDF is just an icon");
        assertTrue(javax.swing.SwingUtilities.isDescendingFrom(ribbon.saveButton(), bar.getTitleRow()));
        assertTrue(javax.swing.SwingUtilities.isDescendingFrom(ribbon.exportButton(), bar.getTitleRow()));
    }

    @Test
    void exportWritesEverySheetWithInkOrItemsThenTheAppendix(@TempDir Path dir) throws Exception {
        insertCalculation();                                   // sheet 1, and a calculation for the appendix
        drawing.setTool(DrawingCanvas.Tool.PEN);
        drag(100, SECOND_SHEET + 100, 200, SECOND_SHEET + 150);  // ink on sheet 2
        LibraryPanel panel = new LibraryPanel(library, items::insert, () -> { });
        DocumentRibbon ribbon = new DocumentRibbon(new Ribbon("Math Lab"), canvas, controller, items, panel);
        Path pdf = dir.resolve("notes.pdf");
        ribbon.exportTo(pdf);
        try (PDDocument document = PDDocument.load(pdf.toFile())) {
            assertEquals(3, document.getNumberOfPages(), "two sheets, then the appendix");
        }
    }
}
