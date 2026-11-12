package pdf_editor;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Source-linked materials are hidden, not destroyed, so Canvas Undo can restore them. */
class MathLibraryRestoreTest {
    @Test void erasedCalculationReturnsWithOriginalIdentityOnCanvasUndo() {
        MathLibrary library = new MathLibrary();
        MathLibrary.Material first = library.addSourceCalculation(12, "a+b", "5");
        library.retainActiveSources(Set.of());
        assertNull(library.find(first.id()));
        library.retainActiveSources(Set.of(12L));
        assertSame(first, library.find(first.id()));
    }
    @Test void unrelatedSourceIsNotHidden() {
        MathLibrary library = new MathLibrary();
        var first = library.addSourceCalculation(1, "a", "1");
        var second = library.addSourceCalculation(2, "b", "2");
        library.retainActiveSources(Set.of(2L));
        assertNull(library.find(first.id()));
        assertSame(second, library.find(second.id()));
    }
    @Test void recalculatingReplacesSuspendedMaterialNotDuplicates() {
        MathLibrary library = new MathLibrary();
        var first = library.addSourceCalculation(5, "x", "3");
        library.retainActiveSources(Set.of());
        var second = library.addSourceCalculation(5, "x", "4");
        assertNull(library.find(first.id()));
        assertEquals(1, library.items().size());
        library.retainActiveSources(Set.of(5L));
        assertSame(second, library.items().get(0));
    }
    @Test void explicitLibraryDeleteThenUndoRetainsCanvasOrigin() {
        MathLibrary library = new MathLibrary();
        var item = library.addSourceCalculation(18, "x", "7");
        assertTrue(library.delete(item.id()));
        assertTrue(library.undoDelete());
        library.retainActiveSources(Set.of());
        assertNull(library.find(item.id()));
        library.retainActiveSources(Set.of(18L));
        assertSame(item, library.find(item.id()));
    }
    @Test void manuallyDeletedItemDoesNotReappearOnCanvasChange() {
        MathLibrary library = new MathLibrary();
        var item = library.addSourceCalculation(18, "x", "7");
        assertTrue(library.delete(item.id()));
        library.retainActiveSources(Set.of());
        library.retainActiveSources(Set.of(18L));
        assertNull(library.find(item.id()));
    }
    @Test void unlinkedHistoricalMaterialSurvivesCanvasErase() {
        MathLibrary library = new MathLibrary();
        var item = library.addFormula("a+b");
        library.retainActiveSources(Set.of());
        assertSame(item, library.find(item.id()));
    }
    @Test void repeatedCanvasSyncIsIdempotent() {
        MathLibrary library = new MathLibrary();
        var item = library.addSourceCalculation(2, "a", "1");
        library.retainActiveSources(Set.of());
        library.retainActiveSources(Set.of());
        library.retainActiveSources(Set.of(2L));
        library.retainActiveSources(Set.of(2L));
        assertEquals(1, library.items().size());
        assertSame(item, library.find(item.id()));
    }
}
