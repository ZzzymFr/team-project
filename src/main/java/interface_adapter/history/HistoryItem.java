package interface_adapter.history;

import history.domain.HistoryEntry;
import java.util.Objects;

/** Presentation data for persistent History. A record is never mutable through the UI. */
public final class HistoryItem {
    private final HistoryEntry entry;
    public HistoryItem(HistoryEntry entry) { this.entry = Objects.requireNonNull(entry); }
    public HistoryEntry entry() { return entry; }
    public String getId() { return entry.id(); }
    public String getLatex() { return entry.formula(); }
    public String getAnswer() { return entry.result(); }
    public boolean isGraph() { return entry.kind() == HistoryEntry.Kind.GRAPH; }
    public byte[] imagePng() { return entry.png(); }
}
