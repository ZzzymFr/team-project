package history.use_case;

import history.domain.HistoryEntry;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Session-only origin links for saved calculations. Deleting canvas ink temporarily
 * removes only records created by this session's source calculation. Restoring the
 * original formula via Canvas Undo restores the exact same record. Historical records
 * from earlier sessions (and graph images without a stroke origin) remain durable.
 *
 * This use case has no Swing or file-system dependencies.
 */
public final class CanvasHistoryLifecycle {
    private record Linked(HistoryEntry record, boolean active) { }
    private final HistoryInteractor history;
    private final Map<Long, java.util.List<Linked>> linked = new HashMap<>();

    public CanvasHistoryLifecycle(HistoryInteractor history) {
        this.history = Objects.requireNonNull(history);
    }

    public synchronized boolean recordCalculation(long sourceId, String latex, String result) throws IOException {
        if (latex == null || latex.isBlank() || result == null || result.isBlank()) return false;
        boolean newlySaved = history.recordCalculation(latex, result);
        if (newlySaved) {
            for (HistoryEntry entry : history.list()) {
                if (entry.kind() == HistoryEntry.Kind.CALCULATION
                        && entry.formula().equals(latex.trim()) && entry.result().equals(result.trim())) {
                    linked.computeIfAbsent(sourceId, ignored -> new java.util.ArrayList<>())
                            .add(new Linked(entry, true));
                    break;
                }
            }
        }
        return newlySaved;
    }

    public synchronized void synchronize(Set<Long> activeSources) throws IOException {
        Objects.requireNonNull(activeSources);
        for (var item : Map.copyOf(linked).entrySet()) {
            boolean shouldBeActive = activeSources.contains(item.getKey());
            java.util.List<Linked> next = new java.util.ArrayList<>();
            for (Linked state : item.getValue()) {
                if (state.active() && !shouldBeActive) {
                    if (history.suspendLinked(state.record().id())) {
                        next.add(new Linked(state.record(), false));
                    }
                    // An explicitly deleted record is not restored by Canvas Undo.
                } else if (!state.active() && shouldBeActive) {
                    history.restoreLinked(state.record());
                    next.add(new Linked(state.record(), true));
                } else {
                    next.add(state);
                }
            }
            if (next.isEmpty()) linked.remove(item.getKey());
            else linked.put(item.getKey(), next);
        }
    }
}
