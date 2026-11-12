package pdf_editor.use_case;

import org.junit.jupiter.api.Test;
import pdf_editor.MathLibrary;
import pdf_editor.domain.PdfDocument;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DocumentEditorBoundaryRegressionTest {
    private final MathLibrary library = new MathLibrary();
    private final DocumentEditor editor = new DocumentEditor();

    @Test void undoRedoSplitRestoresOriginalAndSplitAgain() {
        var original = editor.place(0, library.addCalculation("5+5=", "=10"), 15, 20, 200, 80);
        var parts = editor.splitCalculation(0, original.id());
        assertEquals(2, editor.placements(0).size());
        assertNull(editor.find(0, original.id()));
        assertTrue(editor.undo());
        assertNotNull(editor.find(0, original.id()));
        assertEquals(1, editor.placements(0).size());
        assertTrue(editor.redo());
        assertEquals(2, editor.placements(0).size());
        assertNotNull(editor.find(0, parts[0].id()));
    }
    @Test void invalidSplitDoesNotCreateUndoCheckpoint() {
        var p = editor.place(0, library.addFormula("x"), 0, 0, 200, 80);
        assertNull(editor.splitCalculation(0, p.id()));
        assertTrue(editor.undo()); // Only undo placement, not a phantom split
        assertTrue(editor.placements(0).isEmpty());
    }
    @Test void redoIsInvalidatedByNewEditAfterUndo() {
        editor.place(0, library.addFormula("a"), 0, 0, 200, 80);
        editor.place(0, library.addFormula("b"), 0, 90, 200, 80);
        assertTrue(editor.undo());
        editor.addNote(0, "replace undone change", 0, 200);
        assertFalse(editor.redo());
    }
    @Test void undoHistoryHasSixtyEntryLimit() {
        for (int i = 0; i < 75; i++) editor.addNote(0, "n" + i, 10, 10);
        int count = 0;
        while (editor.undo()) count++;
        assertEquals(60, count);
        assertEquals(15, editor.placements(0).size());
    }
    @Test void emptyPageRemovalIsForbidden() {
        assertThrows(IllegalStateException.class, () -> editor.removePage(0));
        assertEquals(1, editor.pageCount());
    }
    @Test void nonFiniteBoundsFailWithoutPollutingHistory() {
        assertThrows(IllegalArgumentException.class,
                () -> editor.place(0, library.addFormula("x"), Float.NaN, 0, 100, 70));
        assertTrue(editor.placements(0).isEmpty());
        assertFalse(editor.undo());
    }
    @Test void invalidNoteLocationDoesNotConsumeUndoOrRedo() {
        var placed = editor.place(0, library.addFormula("x"), 10, 20, 100, 70);
        assertTrue(editor.undo());
        assertThrows(IllegalArgumentException.class,
                () -> editor.addNote(0, "note", Float.POSITIVE_INFINITY, 20));
        assertFalse(editor.undo(), "Failed note insert must not create history");
        assertTrue(editor.redo(), "Failed note insert must not erase redo");
        assertNotNull(editor.find(0, placed.id()));
    }
    @Test void invalidFormulaPlacementDoesNotConsumeRedo() {
        editor.place(0, library.addFormula("x"), 10, 20, 100, 70);
        assertTrue(editor.undo());
        assertThrows(IllegalArgumentException.class,
                () -> editor.place(0, library.addFormula("y"), Float.NaN, 20, 100, 70));
        assertTrue(editor.redo());
        assertEquals(1, editor.placements(0).size());
    }

    @Test void nestedTransactionRollsBackEverything() {
        assertThrows(IllegalStateException.class, () -> editor.transaction(() -> {
            editor.place(0, library.addFormula("x"), 1, 1, 100, 70);
            editor.transaction(() -> editor.addPage());
        }));
        assertTrue(editor.placements(0).isEmpty());
        assertFalse(editor.undo());
    }
    @Test void acrossPageTransferCanBeUndoneAndRedone() {
        var p = editor.place(0, library.addFormula("x"), 10, 20, 200, 80);
        editor.addPage();
        assertTrue(editor.moveToPage(0, 1, p.id()));
        assertNull(editor.find(0, p.id()));
        assertTrue(editor.undo());
        assertNotNull(editor.find(0, p.id()));
        assertTrue(editor.redo());
        assertNotNull(editor.find(1, p.id()));
    }
    @Test void noOpArrangeAndMissingDeleteDoNotAddHistory() {
        var p = editor.place(0, library.addFormula("x"), 5, 5, 100, 70);
        editor.arrange(0, p.id(), 2);
        assertFalse(editor.delete(0, "missing"));
        assertTrue(editor.undo());
        assertTrue(editor.placements(0).isEmpty());
        assertFalse(editor.undo());
    }
    @Test void editingGraphAsLatexIsRejected() {
        var graph = MathLibrary.region("plot", new java.awt.image.BufferedImage(16, 16,
                java.awt.image.BufferedImage.TYPE_INT_RGB));
        var p = editor.place(0, graph, 10, 10, 200, 90);
        assertThrows(IllegalArgumentException.class, () -> editor.editFormula(0, p.id(), "x", "2"));
        assertTrue(editor.undo());
        assertTrue(editor.placements(0).isEmpty());
    }
}
