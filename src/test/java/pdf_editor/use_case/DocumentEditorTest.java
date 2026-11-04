package pdf_editor.use_case;

import org.junit.jupiter.api.Test;
import pdf_editor.MathLibrary;
import pdf_editor.domain.PdfDocument;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DocumentEditorTest {
    private final MathLibrary materials = new MathLibrary();
    private final DocumentEditor editor = new DocumentEditor();

    @Test void placementIsImmutableAndSnapshotCannotChangeEditor() {
        var p = editor.place(0, materials.addCalculation("1+1", "2"), 20, 30, 300, 100);
        PdfDocument snapshot = editor.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.placements(0).clear());
        snapshot.remove(0, p.id());
        assertEquals(1, editor.placements(0).size());
        assertEquals(20f, editor.find(0, p.id()).x(), 0.001);
    }

    @Test void moveResizeAndUndoRedoPreserveIdentity() {
        var p = editor.place(0, materials.addFormula("x^2"), 10, 20, 300, 100);
        editor.beginTransform(0, p.id());
        var updated = editor.transform(0, p.id(), 50, 60, 200, 150);
        assertEquals(p.id(), updated.id());
        assertEquals(50f, editor.find(0, p.id()).x(), .001);
        assertEquals(200f, editor.find(0, p.id()).width(), .001);
        assertTrue(editor.undo());
        assertEquals(10f, editor.find(0, p.id()).x(), .001);
        assertTrue(editor.redo());
        assertEquals(50f, editor.find(0, p.id()).x(), .001);
    }

    @Test void splitKeepsSingleEqualsAndIsUndoable() {
        var p = editor.place(0, materials.addCalculation("1+1=", "=2"), 20, 30, 300, 90);
        var split = editor.splitCalculation(0, p.id());
        assertNotNull(split);
        assertEquals("1+1", split[0].material().formula());
        assertEquals("=2", split[1].material().result());
        assertEquals(2, editor.placements(0).size());
        assertTrue(editor.undo());
        assertEquals(1, editor.placements(0).size());
        assertEquals(p.id(), editor.placements(0).get(0).id());
    }

    @Test void notesRichFormattingAndPageTransfer() {
        var note = editor.addNote(0, "note", 10, 10);
        editor.updateRichNote(0, note.id(), List.of(new PdfDocument.NoteSpan("nihao", true, false, true, 16)));
        assertEquals("nihao", editor.find(0, note.id()).noteText());
        int page2 = editor.addPage();
        assertTrue(editor.moveToPage(0, page2, note.id()));
        assertEquals(0, editor.placements(0).size());
        assertEquals("nihao", editor.placements(page2).get(0).noteText());
        assertTrue(editor.undo());
        assertEquals(1, editor.placements(0).size());
    }

    @Test void transactionIsAtomicAndOnlyOneUndo() {
        var material = materials.addFormula("y=x^2");
        editor.transaction(() -> {
            editor.place(0, material, 10, 10, 200, 80);
            int next = editor.addPage();
            editor.place(next, material, 10, 10, 200, 80);
        });
        assertEquals(2, editor.pageCount());
        assertTrue(editor.undo());
        assertEquals(1, editor.pageCount());
        assertTrue(editor.placements(0).isEmpty());
        assertFalse(editor.undo());
    }

    @Test void transactionRollsBackOnFailure() {
        var material = materials.addFormula("a");
        assertThrows(IllegalArgumentException.class, () -> editor.transaction(() -> {
            editor.place(0, material, 10, 10, 200, 80);
            editor.place(0, material, Float.NaN, 10, 200, 80);
        }));
        assertTrue(editor.placements(0).isEmpty());
        assertFalse(editor.undo());
    }

    @Test void boundariesPreventMaterialFromLeavingPage() {
        var p = editor.place(0, materials.addFormula("a"), 4000, -50, 100, 50);
        assertEquals(PdfDocument.PAGE_WIDTH - 100, p.x(), .001);
        assertEquals(0f, p.y(), .001);
        editor.beginTransform(0, p.id());
        var changed = editor.transform(0, p.id(), -40, 9999, 700, 40);
        assertEquals(PdfDocument.PAGE_WIDTH, changed.width(), .001);
        assertEquals(0f, changed.x(), .001);
        assertEquals(PdfDocument.PAGE_HEIGHT - 40, changed.y(), .001);
    }
}
