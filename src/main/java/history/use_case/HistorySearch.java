package history.use_case;

import history.domain.HistoryEntry;
import java.util.Locale;
import java.util.Objects;

/** Pure search policy shared by the History view and testable without Swing. */
public final class HistorySearch {
    private HistorySearch() { }
    public static boolean matches(HistoryEntry entry, String query) {
        Objects.requireNonNull(entry);
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return true;
        return (entry.title() + " " + entry.kind() + " " + entry.formula() + " " + entry.result())
                .toLowerCase(Locale.ROOT).contains(needle);
    }
}
