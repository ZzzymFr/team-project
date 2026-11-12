package history.use_case;

import history.domain.HistoryEntry;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercise actual origin-linked history storage, not only a Swing menu. */
class CanvasHistoryLifecycleTest {
    static final class Repository implements HistoryRepository {
        private final Map<String, HistoryEntry> saved = new LinkedHashMap<>();
        @Override public boolean saveIfAbsent(HistoryEntry record) {
            if (saved.values().stream().anyMatch(x -> x.contentKey().equals(record.contentKey()))) return false;
            saved.put(record.id(), record); return true;
        }
        @Override public List<HistoryEntry> findAll() { return List.copyOf(saved.values()); }
        @Override public boolean deleteById(String id) {return saved.remove(id) != null;}
    }
    @Test void eraseAndUndoRestoreExactlySameHistoryRecord() throws Exception {
        var history = new HistoryInteractor(new Repository());
        var lifecycle = new CanvasHistoryLifecycle(history);
        assertTrue(lifecycle.recordCalculation(7L, "1+1", "2"));
        HistoryEntry entry = history.list().get(0);
        lifecycle.synchronize(Set.of()); assertTrue(history.list().isEmpty());
        lifecycle.synchronize(Set.of(7L));
        assertEquals(1, history.list().size());
        assertEquals(entry.id(), history.list().get(0).id());
        assertEquals(entry.createdAt(), history.list().get(0).createdAt());
    }
    @Test void cannotPruneOldHistoryCreatedBeforeThisCanvasSession() throws Exception {
        var history = new HistoryInteractor(new Repository());
        history.recordCalculation("old", "9");
        var lifecycle = new CanvasHistoryLifecycle(history);
        assertFalse(lifecycle.recordCalculation(100L, "old", "9"));
        lifecycle.synchronize(Set.of());
        assertEquals("old", history.list().get(0).formula());
    }
    @Test void deletingOneFormulaDoesNotDeleteAnother() throws Exception {
        var history = new HistoryInteractor(new Repository());
        var lifecycle = new CanvasHistoryLifecycle(history);
        lifecycle.recordCalculation(1L,"a","2");lifecycle.recordCalculation(2L,"b","3");
        lifecycle.synchronize(Set.of(2L));
        assertEquals(1,history.list().size());assertEquals("b",history.list().get(0).formula());
        lifecycle.synchronize(Set.of(1L,2L));assertEquals(2,history.list().size());
    }
    @Test void validFormulaWithoutNumericalResultIsLinkedButEmptyFormulaIsNot() throws Exception {
        var history = new HistoryInteractor(new Repository());
        var lifecycle = new CanvasHistoryLifecycle(history);
        assertTrue(lifecycle.recordCalculation(3L, "y=3x", " "));
        assertEquals(HistoryEntry.Kind.FORMULA, history.list().get(0).kind());
        assertFalse(lifecycle.recordCalculation(3L, null, "2"));
        lifecycle.synchronize(Set.of()); assertEquals(0, history.list().size());
        lifecycle.synchronize(Set.of(3L)); assertEquals(1, history.list().size());
    }
    @Test void manuallyDeletedHistoryNotResurrectedByCanvasUndo() throws Exception {
        var history = new HistoryInteractor(new Repository());
        var lifecycle = new CanvasHistoryLifecycle(history);
        lifecycle.recordCalculation(5L, "x+1", "4");
        history.delete(history.list().get(0).id());
        lifecycle.synchronize(Set.of());lifecycle.synchronize(Set.of(5L));
        assertTrue(history.list().isEmpty());
    }
    @Test void graphHistoryRemainsWhenCanvasFormulaErased() throws Exception {
        var history = new HistoryInteractor(new Repository());
        var lifecycle = new CanvasHistoryLifecycle(history);
        byte[] png={(byte)137,80,78,71,13,10,26,10,1};
        history.recordGraph("y=x",png,1,1);
        lifecycle.recordCalculation(9L,"1+1","2");
        lifecycle.synchronize(Set.of());
        assertEquals(HistoryEntry.Kind.GRAPH,history.list().get(0).kind());
    }
    @Test void duplicateCanvasSyncNeverDuplicatesHistory() throws Exception {
        var history = new HistoryInteractor(new Repository());
        var lifecycle = new CanvasHistoryLifecycle(history);
        lifecycle.recordCalculation(7L,"f","42");
        for(int i=0;i<4;i++) { lifecycle.synchronize(Set.of());lifecycle.synchronize(Set.of(7L)); }
        assertEquals(1,history.list().size());
    }
}
