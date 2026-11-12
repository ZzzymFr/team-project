package pdf_editor.view;

import history.domain.HistoryEntry;
import interface_adapter.history.HistoryItem;
import org.junit.jupiter.api.Test;
import pdf_editor.MathLibrary;
import pdf_editor.adapter.PdfEditorController;
import pdf_editor.use_case.DocumentEditor;
import pdf_editor.use_case.AutoLayoutInteractor;
import pdf_editor.use_case.PdfExportInteractor;
import view.board.PdfPanel;
import view.board.doc.DocEditorPanel;

import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Integration of the peer Word UI with the existing Clean Architecture PDF use cases. */
class EmbeddedPdfEditorTest {
    private PdfEditorController controller() {
        DocumentEditor editor = new DocumentEditor();
        return new PdfEditorController(new MathLibrary(), editor, new AutoLayoutInteractor(editor),
                new PdfExportInteractor((document, file) -> {}, (document, dpi) -> List.of()));
    }
    private HistoryItem history(String latex, String result) {
        return new HistoryItem(new HistoryEntry("entry", HistoryEntry.Kind.CALCULATION,
                "Calculation", latex, result, null, 0, 0, Instant.EPOCH));
    }
    @Test void wordDocumentPanelIsEmbeddedAndNotAStandaloneWindow() {
        PdfPanel panel = new PdfPanel(controller());
        assertTrue(panel.editor() instanceof DocEditorPanel);
        assertTrue(panel instanceof javax.swing.JPanel);
        assertFalse(panel.editor().isLibraryVisible());
        panel.close();
    }
    @Test void historyIsCopiedToEditableDocumentWithoutModifyingStoredRecord() {
        PdfEditorController controller = controller();
        PdfPanel panel = new PdfPanel(controller);
        HistoryItem saved = history("x+1", "2");
        panel.addHistoryItem(saved);
        assertEquals(1, controller.placements().size());
        assertTrue(controller.editSelectedFormula("x+2", "3"));
        assertEquals("x+1", saved.entry().formula());
        assertEquals("x+2", controller.placements().get(0).material().formula());
        assertTrue(controller.undo());
        assertEquals("x+1", controller.placements().get(0).material().formula());
        panel.close();
    }
    @Test void documentPersistsAcrossPagesAndLayoutModeIsExternalToModel() {
        PdfEditorController controller = controller();
        PdfPanel panel = new PdfPanel(controller);
        panel.addHistoryItem(history("x", "2"));
        controller.addPage();
        assertEquals(2, controller.pageCount());
        controller.switchPage(0);
        assertEquals(1, controller.placements().size());
        panel.fitWhenOpened();
        assertEquals(1, controller.placements().size());
        panel.close();
    }
    @Test void restoredGraphRetainsImageDimensionsAndAspectRatio() {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(180, 360, 2);
        java.io.ByteArrayOutputStream stream = new java.io.ByteArrayOutputStream();
        assertDoesNotThrow(() -> javax.imageio.ImageIO.write(image, "png", stream));
        HistoryItem saved = new HistoryItem(new HistoryEntry("g", HistoryEntry.Kind.GRAPH,
                "Graph", "y=x", "", stream.toByteArray(), 180, 360, Instant.EPOCH));
        PdfEditorController controller = controller();
        PdfPanel panel = new PdfPanel(controller);
        panel.addHistoryItem(saved);
        assertTrue(controller.placements().get(0).material().isGraph());
        assertEquals(180, controller.placements().get(0).material().imageWidth());
        assertEquals(2.0, controller.placements().get(0).height() /
                controller.placements().get(0).width(), .01);
        panel.close();
    }
}
