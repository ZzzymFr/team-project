package pdf_editor.use_case;

import org.junit.jupiter.api.Test;
import pdf_editor.MathLibrary;
import pdf_editor.domain.PdfDocument;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AutoLayoutBoundaryTest {
    @Test void manyFormulasPaginateAndAllItemsRemainOnA4() {
        var library = new MathLibrary();
        var items = new ArrayList<MathLibrary.Material>();
        for (int i=0; i<30; i++) items.add(library.addFormula("x_"+i));
        var editor = new DocumentEditor();
        int last = new AutoLayoutInteractor(editor).importMaterials(0, items);
        assertTrue(last >= 3);
        int count = 0;
        for (int page=0; page<editor.pageCount(); page++) {
            for (var p : editor.placements(page)) {
                count++;
                assertTrue(p.y() >= 0);
                assertTrue(p.y() + p.height() <= PdfDocument.PAGE_HEIGHT);
            }
        }
        assertEquals(30, count);
        assertTrue(editor.undo());
        assertEquals(1, editor.pageCount());
        assertTrue(editor.placements(0).isEmpty());
    }
    @Test void invalidPageRollsBackWithoutChangingUndoHistory() {
        var editor = new DocumentEditor();
        var material = new MathLibrary().addFormula("x");
        assertThrows(IndexOutOfBoundsException.class,
                () -> new AutoLayoutInteractor(editor).importMaterials(2, List.of(material)));
        assertEquals(1, editor.pageCount());
        assertFalse(editor.undo());
    }
    @Test void nullMaterialRollsBackBatchAndKeepsExistingItems() {
        var editor = new DocumentEditor();
        var material = new MathLibrary().addFormula("x");
        var previous = editor.place(0, material, 10, 10, 200, 80);
        assertThrows(NullPointerException.class,
                () -> new AutoLayoutInteractor(editor).importMaterials(0, java.util.Arrays.asList(material, null)));
        assertEquals(1, editor.placements(0).size());
        assertNotNull(editor.find(0, previous.id()));
    }
    @Test void pairedGraphAndFormulaStayTogetherWhenNearBottom() {
        var library = new MathLibrary();
        library.addGraph("y=x^2", new BufferedImage(400, 280, BufferedImage.TYPE_INT_RGB));
        var editor = new DocumentEditor();
        editor.addNote(0, "bottom", 20, 700);
        int last = new AutoLayoutInteractor(editor).importMaterials(0, library.items());
        assertEquals(1, last);
        assertEquals(2, editor.placements(1).size());
        assertFalse(editor.placements(1).get(0).material().isGraph());
        assertTrue(editor.placements(1).get(1).material().isGraph());
    }
}
