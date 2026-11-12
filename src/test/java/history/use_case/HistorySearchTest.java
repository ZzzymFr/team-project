package history.use_case;

import history.domain.HistoryEntry;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class HistorySearchTest {
    private final HistoryEntry entry = new HistoryEntry("x", HistoryEntry.Kind.CALCULATION,
            "Calculation", "\\alpha + x", "结果 5", null, 0, 0, Instant.EPOCH);
    @Test void blankSearchMatchesAll() { assertTrue(HistorySearch.matches(entry, "  ")); assertTrue(HistorySearch.matches(entry, null)); }
    @Test void searchesLatexCaseInsensitive() { assertTrue(HistorySearch.matches(entry, "ALPHA")); }
    @Test void searchesResultAndTitle() { assertTrue(HistorySearch.matches(entry, "结果")); assertTrue(HistorySearch.matches(entry, "calculation")); }
    @Test void notMatchingReturnsFalse() { assertFalse(HistorySearch.matches(entry, "integral")); }
    @Test void rejectsNullEntry() { assertThrows(NullPointerException.class, () -> HistorySearch.matches(null, "x")); }
}
