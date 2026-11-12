package history.use_case;

import history.domain.HistoryEntry;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HistoryInteractorTest {
    private static final class Fake implements HistoryRepository {
        final List<HistoryEntry> entries = new ArrayList<>();
        @Override public boolean saveIfAbsent(HistoryEntry item) {
            if (entries.stream().anyMatch(e -> e.contentKey().equals(item.contentKey()))) return false;
            entries.add(item); return true;
        }
        @Override public List<HistoryEntry> findAll() { return List.copyOf(entries); }
        @Override public boolean deleteById(String id) { return entries.removeIf(e -> e.id().equals(id)); }
    }
    private HistoryInteractor useCase(Fake fake) {
        return new HistoryInteractor(fake, Clock.fixed(Instant.parse("2026-11-04T20:00:00Z"), ZoneOffset.UTC));
    }
    @Test void savesFormulaWithoutResult() throws Exception {
        Fake fake = new Fake(); HistoryInteractor uc = useCase(fake);
        assertTrue(uc.recordFormula(" x^2 "));
        assertEquals(HistoryEntry.Kind.FORMULA, uc.list().get(0).kind());
        assertEquals("x^2", uc.list().get(0).formula());
    }
    @Test void savesFormulaAndAnswerTogether() throws Exception {
        Fake fake = new Fake(); HistoryInteractor uc = useCase(fake);
        assertTrue(uc.recordCalculation("1+1", "2"));
        assertEquals("2", uc.list().get(0).result());
        assertEquals(HistoryEntry.Kind.CALCULATION, uc.list().get(0).kind());
    }
    @Test void repeatedCalculationsDeduplicate() throws Exception {
        HistoryInteractor uc = useCase(new Fake());
        assertTrue(uc.recordCalculation("1+1", "2"));
        assertFalse(uc.recordCalculation(" 1+1 ", " 2 "));
        assertEquals(1, uc.list().size());
    }
    @Test void partialFormulaIsUpgradedToFullCalculation() throws Exception {
        HistoryInteractor uc = useCase(new Fake());
        uc.recordFormula("x+1"); uc.recordCalculation("x+1", "5");
        assertEquals(1, uc.list().size());
        assertEquals("5", uc.list().get(0).result());
    }
    @Test void formulaOnlyNotAddedWhenCalculationAlreadyExists() throws Exception {
        HistoryInteractor uc = useCase(new Fake());
        uc.recordCalculation("x", "8");
        assertFalse(uc.recordFormula("x"));
        assertEquals(1, uc.list().size());
    }
    @Test void emptyCalculationBecomesFormulaOnly() throws Exception {
        HistoryInteractor uc = useCase(new Fake());
        assertTrue(uc.recordCalculation(" x ", " "));
        assertEquals(HistoryEntry.Kind.FORMULA, uc.list().get(0).kind());
    }
    @Test void distinctResultsCanBeRetainedAsDifferentHistory() throws Exception {
        HistoryInteractor uc = useCase(new Fake());
        uc.recordCalculation("x", "1"); uc.recordCalculation("x", "2");
        assertEquals(2, uc.list().size());
    }
    @Test void ignoresNullAndBlankInput() throws Exception {
        HistoryInteractor uc = useCase(new Fake());
        assertFalse(uc.recordFormula(null));
        assertFalse(uc.recordCalculation("  ", "1"));
        assertFalse(uc.recordGraph("x", null, 2, 2));
        assertTrue(uc.list().isEmpty());
    }
    @Test void graphStoredOnceWithLinkedSource() throws Exception {
        HistoryInteractor uc = useCase(new Fake());
        byte[] png = {(byte)137,80,78,71,13,10,26,10,1,2,3};
        assertTrue(uc.recordGraph("x^2", png, 3, 4));
        assertFalse(uc.recordGraph("x^2", png, 3, 4));
        assertEquals(HistoryEntry.Kind.GRAPH, uc.list().get(0).kind());
        assertEquals("x^2", uc.list().get(0).formula());
    }
    @Test void findDeleteAndUnknownId() throws Exception {
        HistoryInteractor uc = useCase(new Fake());
        uc.recordCalculation("1+1", "2"); String id = uc.list().get(0).id();
        assertNotNull(uc.find(id));
        assertNull(uc.find("missing"));
        assertTrue(uc.delete(id));
        assertFalse(uc.delete(id));
        assertFalse(uc.delete(null));
    }
    @Test void listNewestFirstAndFixedClock() throws Exception {
        Fake fake = new Fake();
        fake.saveIfAbsent(new HistoryEntry("older", HistoryEntry.Kind.FORMULA, "Formula", "x", "", null,0,0,Instant.EPOCH));
        HistoryInteractor uc = useCase(fake);uc.recordFormula("y");
        assertEquals("y", uc.list().get(0).formula());
        assertEquals(Instant.parse("2026-11-04T20:00:00Z"), uc.list().get(0).createdAt());
    }
    @Test void persistenceErrorPropagatesThroughBoundary() {
        HistoryRepository broken = new HistoryRepository() {
            @Override public boolean saveIfAbsent(HistoryEntry e) throws IOException { throw new IOException("Disk full"); }
            @Override public List<HistoryEntry> findAll() { return List.of(); }
            @Override public boolean deleteById(String id) { return false; }
        };
        assertThrows(IOException.class, () -> new HistoryInteractor(broken).recordCalculation("1+1", "2"));
    }
}
