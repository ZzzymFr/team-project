package pdf_editor.use_case;
import org.junit.jupiter.api.Test;
import pdf_editor.MathLibrary;
import pdf_editor.domain.PdfMaterial;
import pdf_editor.domain.PdfDocument;
import static org.junit.jupiter.api.Assertions.*;

class FormulaEditingTest {
    private final MathLibrary library = new MathLibrary();
    private final DocumentEditor editor = new DocumentEditor();

    @Test void replaceFormula() {
        var p = editor.place(0, library.addFormula("x"), 10, 10, 200, 60);
        assertEquals("x^2", editor.editFormula(0, p.id(), "x^2", "").material().formula());
    }
    @Test void replaceResult() {
        var p = editor.place(0, library.addCalculation("1+1", "2"), 10, 10, 200, 60);
        assertEquals("4", editor.editFormula(0, p.id(), "2+2", "4").material().result());
    }
    @Test void undoRestoresOriginalFormula() {
        var p = editor.place(0, library.addFormula("x"), 10, 10, 200, 60);
        editor.editFormula(0, p.id(), "y", ""); assertTrue(editor.undo());
        assertEquals("x", editor.find(0, p.id()).material().formula());
    }
    @Test void redoRestoresCorrection() {
        var p = editor.place(0, library.addFormula("x"), 10, 10, 200, 60);
        editor.editFormula(0, p.id(), "y", ""); editor.undo(); assertTrue(editor.redo());
        assertEquals("y", editor.find(0, p.id()).material().formula());
    }
    @Test void correctionPreservesPlacementIdentity() {
        var p = editor.place(0, library.addFormula("a"), 10, 10, 200, 60);
        assertEquals(p.id(), editor.editFormula(0, p.id(), "b", "").id());
    }
    @Test void correctionPreservesCoordinates() {
        var p = editor.place(0, library.addFormula("a"), 54, 33, 220, 80);
        var result = editor.editFormula(0, p.id(), "b", "");
        assertEquals(54, result.x()); assertEquals(33, result.y());
        assertEquals(220, result.width()); assertEquals(80, result.height());
    }
    @Test void correctionDoesNotChangeLibrarySource() {
        var original = library.addFormula("original"); var p = editor.place(0, original, 10, 10, 200, 60);
        editor.editFormula(0, p.id(), "corrected", "");
        assertEquals("original", original.formula());
        assertEquals("original", library.find(original.id()).formula());
    }
    @Test void rejectsNote() {
        var note = editor.addNote(0, "hello", 10, 10);
        assertThrows(IllegalArgumentException.class, () -> editor.editFormula(0, note.id(), "x", ""));
    }
    @Test void rejectsUnknownPlacement() {
        assertThrows(IllegalArgumentException.class, () -> editor.editFormula(0, "missing", "x", ""));
    }
    @Test void rejectsNullFormulaWithoutMutation() {
        var p = editor.place(0, library.addFormula("old"), 10, 10, 200, 60);
        assertThrows(NullPointerException.class, () -> editor.editFormula(0, p.id(), null, ""));
        assertEquals("old", editor.find(0, p.id()).material().formula());
    }
    @Test void acceptsLaTexSyntaxVerbatim() {
        var p = editor.place(0, library.addFormula("?"), 10, 10, 200, 60);
        assertEquals("\\frac{1}{2}", editor.editFormula(0, p.id(), "\\frac{1}{2}", "").material().formula());
    }
    @Test void acceptsKeyboardTextVerbatim() {
        var p = editor.place(0, library.addFormula("?"), 10, 10, 200, 60);
        assertEquals("hello world", editor.editFormula(0, p.id(), "hello world", "").material().formula());
    }
    @Test void editingOneOfTwoPlacementsDoesNotChangeOther() {
        var shared = library.addFormula("a");
        var first = editor.place(0, shared, 10, 10, 200, 60);
        var second = editor.place(0, shared, 10, 100, 200, 60);
        editor.editFormula(0, first.id(), "changed", "");
        assertEquals("a", editor.find(0, second.id()).material().formula());
    }
    @Test void editingFormulaWithResultRetainsOnePlacement() {
        var p = editor.place(0, library.addCalculation("1+1", "2"), 10, 10, 200, 60);
        editor.editFormula(0, p.id(), "1+2", "3");
        assertEquals(1, editor.placements(0).size());
    }
    @Test void splitUsesEditedFormula() {
        var p = editor.place(0, library.addCalculation("1+1", "2"), 10, 10, 300, 60);
        editor.editFormula(0, p.id(), "4+4", "8");
        assertEquals("4+4", editor.splitCalculation(0, p.id())[0].material().formula());
    }
}
