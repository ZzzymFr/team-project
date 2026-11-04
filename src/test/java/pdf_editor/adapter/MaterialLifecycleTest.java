package pdf_editor.adapter;
import org.junit.jupiter.api.Test;
import pdf_editor.MathLibrary;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class MaterialLifecycleTest {
    @Test void bindsSourceIdToMaterial() {
        var library = new MathLibrary(); var m = library.addSourceCalculation(1, "x", "2");
        assertSame(m, library.find(m.id()));
    }
    @Test void eraseRemovesOnlyOneSource() {
        var library = new MathLibrary(); var a = library.addSourceCalculation(1, "a", "1");
        var b = library.addSourceCalculation(2, "b", "2");
        library.removeSource(1);
        assertNull(library.find(a.id())); assertNotNull(library.find(b.id()));
    }
    @Test void retainingActiveSourcesRemovesErasedOnes() {
        var library = new MathLibrary(); library.addSourceCalculation(1, "a", "1");
        library.addSourceCalculation(2, "b", "2"); library.retainActiveSources(Set.of(2L));
        assertEquals(1, library.items().size());
    }
    @Test void retainingActiveSourcesPreservesCurrentAnswers() {
        var library = new MathLibrary(); library.addSourceCalculation(7, "a", "1");
        library.retainActiveSources(Set.of(7L)); assertEquals(1, library.items().size());
    }
    @Test void noOpMissingSource() {
        var library = new MathLibrary(); library.addFormula("a"); library.removeSource(999);
        assertEquals(1, library.items().size());
    }
    @Test void recalculateReplacesMaterial() {
        var library = new MathLibrary(); library.addSourceCalculation(7, "a", "1");
        library.addSourceCalculation(7, "a", "2");
        assertEquals(1, library.items().size()); assertEquals("2", library.items().get(0).result());
    }
    @Test void clearRemovesAllMaterials() {
        var library = new MathLibrary(); library.addFormula("a");
        library.addSourceCalculation(7, "b", "1"); library.clear(); assertTrue(library.items().isEmpty());
    }
    @Test void clearResetsSourceMapping() {
        var library = new MathLibrary(); library.addSourceCalculation(7, "b", "1");
        library.clear(); library.addSourceCalculation(7, "c", "2");
        assertEquals(1, library.items().size());
    }
    @Test void clearNotifiesSubscribers() {
        var library = new MathLibrary(); AtomicInteger calls = new AtomicInteger();
        library.onChange(calls::incrementAndGet); library.addFormula("a"); library.clear();
        assertEquals(2, calls.get());
    }
    @Test void removeSourceNotifiesSubscribers() {
        var library = new MathLibrary(); AtomicInteger calls = new AtomicInteger();
        library.onChange(calls::incrementAndGet); library.addSourceCalculation(2, "a", "1");
        library.removeSource(2); assertEquals(2, calls.get());
    }
    @Test void unsubscriptionWorks() {
        var library = new MathLibrary(); AtomicInteger calls = new AtomicInteger();
        Runnable unsubscribe = library.onChange(calls::incrementAndGet); unsubscribe.run();
        library.addFormula("a"); assertEquals(0, calls.get());
    }
    @Test void exposedItemsCannotBeClearedByCaller() {
        var library = new MathLibrary(); library.addFormula("a");
        assertThrows(UnsupportedOperationException.class, () -> library.items().clear());
    }
}
