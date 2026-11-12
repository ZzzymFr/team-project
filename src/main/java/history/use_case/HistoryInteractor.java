package history.use_case;

import history.domain.HistoryEntry;
import java.io.IOException;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** US5: auto-save, deduplication, browse and delete; independent of Swing and files. */
public final class HistoryInteractor {
    private final HistoryRepository repository;
    private final Clock clock;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    public Runnable onChange(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener));
        return () -> listeners.remove(listener);
    }
    private void changed() { for (Runnable listener : listeners) listener.run(); }
    public HistoryInteractor(HistoryRepository repository) { this(repository, Clock.systemUTC()); }
    public HistoryInteractor(HistoryRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    public boolean recordFormula(String latex) throws IOException {
        if (latex == null || latex.isBlank()) return false;
        String formula = latex.trim();
        // A calculation already containing this formula is more informative than a formula-only copy.
        for (HistoryEntry previous : repository.findAll()) {
            if (previous.kind() == HistoryEntry.Kind.CALCULATION && previous.formula().equals(formula))
                return false;
        }
        boolean saved = repository.saveIfAbsent(newEntry(HistoryEntry.Kind.FORMULA, "Formula", formula, "", null, 0, 0));
        if (saved) changed();
        return saved;
    }

    public boolean recordCalculation(String latex, String result) throws IOException {
        if (latex == null || latex.isBlank()) return false;
        String formula = latex.trim();
        if (result == null || result.isBlank()) return recordFormula(formula);
        HistoryEntry entry = newEntry(HistoryEntry.Kind.CALCULATION, "Calculation", formula,
                result.trim(), null, 0, 0);
        boolean saved = repository.saveIfAbsent(entry);
        // Upgrade earlier formula-only records, including when this calculation already existed.
        boolean removed = false;
        for (HistoryEntry previous : repository.findAll()) {
            if (previous.kind() == HistoryEntry.Kind.FORMULA && previous.formula().equals(formula))
                removed |= repository.deleteById(previous.id());
        }
        if (saved || removed) changed();
        return saved;
    }

    /** Graph source formula and original PNG are stored together, not as duplicate cards. */
    public boolean recordGraph(String source, byte[] png, int width, int height) throws IOException {
        if (png == null) return false;
        boolean saved = repository.saveIfAbsent(newEntry(HistoryEntry.Kind.GRAPH, "Graph",
                source == null ? "" : source.trim(), "", png, width, height));
        if (saved) changed();
        return saved;
    }

    public List<HistoryEntry> list() throws IOException {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(HistoryEntry::createdAt).reversed()
                        .thenComparing(HistoryEntry::id))
                .toList();
    }
    public HistoryEntry find(String id) throws IOException {
        if (id == null) return null;
        return list().stream().filter(record -> record.id().equals(id)).findFirst().orElse(null);
    }
    public boolean delete(String id) throws IOException {
        boolean deleted = id != null && repository.deleteById(id);
        if (deleted) changed();
        return deleted;
    }

    private HistoryEntry newEntry(HistoryEntry.Kind kind, String title, String formula, String result,
                                  byte[] png, int width, int height) {
        return new HistoryEntry(UUID.randomUUID().toString(), kind, title, formula, result,
                png, width, height, clock.instant());
    }
}
