package pdf_editor.use_case;

import org.junit.jupiter.api.Test;
import pdf_editor.MathLibrary;
import pdf_editor.domain.PdfDocument;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class AutoLayoutInteractorTest {
    @Test void groupsAssociatedGraphAndFormulaOnSamePage() {
        MathLibrary library = new MathLibrary();
        DocumentEditor editor = new DocumentEditor();
        editor.addNote(0, "Top notes", 40, 720);
        var graph = new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB);
        library.addGraph("y=x^2", graph);
        AutoLayoutInteractor layout = new AutoLayoutInteractor(editor);
        int last = layout.importMaterials(0, library.items());
        assertEquals(1, last);
        assertEquals(2, editor.placements(1).size());
        assertEquals("y=x^2", editor.placements(1).get(0).material().formula());
        assertFalse(editor.placements(1).get(0).material().isGraph());
        assertTrue(editor.placements(1).get(1).material().isGraph());
    }
    @Test void emptyLibraryLeavesDocumentUnchanged() {
        DocumentEditor editor = new DocumentEditor();
        assertEquals(0, new AutoLayoutInteractor(editor).importMaterials(0, java.util.List.of()));
        assertEquals(1, editor.pageCount());
        assertTrue(editor.placements(0).isEmpty());
    }
}
