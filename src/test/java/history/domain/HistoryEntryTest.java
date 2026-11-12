package history.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class HistoryEntryTest {
    private HistoryEntry formula(String id, String latex) {
        return new HistoryEntry(id, HistoryEntry.Kind.FORMULA, "Formula", latex, "", null, 0, 0, Instant.EPOCH);
    }
    private HistoryEntry calc(String latex, String result) {
        return new HistoryEntry("id", HistoryEntry.Kind.CALCULATION, "Calculation", latex, result,
                null, 0, 0, Instant.EPOCH);
    }
    private static byte[] png() {
        return new byte[] {-119, 80, 78, 71, 13, 10, 26, 10, 0, 1};
    }
    @Test void sameContentDifferentIdOrDateHasSameFingerprint() {
        assertEquals(formula("a", "x^2").contentKey(), formula("b", "x^2").contentKey());
    }
    @Test void whitespaceAtEdgesDoesNotCauseDuplicate() {
        assertEquals(formula("a", " x^2 ").contentKey(), formula("b", "x^2").contentKey());
    }
    @Test void differentResultHasDifferentFingerprint() {
        assertNotEquals(calc("1+1", "2").contentKey(), calc("1+1", "3").contentKey());
    }
    @Test void formulaAndCalculationNeverCollide() {
        assertNotEquals(formula("a", "1+1").contentKey(), calc("1+1", "").contentKey());
    }
    @Test void graphImageChangesFingerprint() {
        byte[] first = png(), second = png(); second[9] = 2;
        HistoryEntry a = graph(first), b = graph(second);
        assertNotEquals(a.contentKey(), b.contentKey());
    }
    @Test void graphPngDefensivelyCopiedOnInputAndOutput() {
        byte[] input = png(); HistoryEntry entry = graph(input);
        input[9] = 99;
        assertEquals(1, entry.png()[9]);
        byte[] exposed = entry.png(); exposed[9] = 88;
        assertEquals(1, entry.png()[9]);
    }
    @Test void rejectsBadGraphDimensionsAndMissingImage() {
        assertThrows(IllegalArgumentException.class,
                () -> new HistoryEntry("x", HistoryEntry.Kind.GRAPH, "Graph", "x", "", png(), 0, 1, Instant.EPOCH));
        assertThrows(IllegalArgumentException.class,
                () -> new HistoryEntry("x", HistoryEntry.Kind.GRAPH, "Graph", "x", "", null, 2, 2, Instant.EPOCH));
    }
    @Test void rejectsNonPngGraphPayload() {
        assertThrows(IllegalArgumentException.class, () -> graph(new byte[]{1, 2, 3}));
    }
    @Test void rejectsImageOnFormula() {
        assertThrows(IllegalArgumentException.class,
                () -> new HistoryEntry("x", HistoryEntry.Kind.FORMULA, "Formula", "x", "", png(), 2, 2, Instant.EPOCH));
    }
    @Test void rejectsEmptyFormulaAndBadMetadata() {
        assertThrows(IllegalArgumentException.class, () -> formula("x", " "));
        assertThrows(IllegalArgumentException.class, () -> formula("", "x"));
        assertThrows(NullPointerException.class, () -> new HistoryEntry("id", null, "Formula", "x", "", null, 0, 0, Instant.EPOCH));
    }
    @Test void supportsUnicodeLatexAndResults() {
        assertEquals(64, calc("\\alpha + 中文", "答案是 2").contentKey().length());
    }
    private HistoryEntry graph(byte[] bytes) {
        return new HistoryEntry("g", HistoryEntry.Kind.GRAPH, "Graph", "x^2", "", bytes, 80, 60, Instant.EPOCH);
    }
}
