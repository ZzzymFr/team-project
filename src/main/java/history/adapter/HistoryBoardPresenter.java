package history.adapter;

import history.domain.HistoryEntry;
import history.use_case.HistoryInteractor;
import interface_adapter.history.HistoryItem;
import interface_adapter.history.HistoryViewModel;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/** Maps history domain records into view data without putting filesystem code in Swing. */
public final class HistoryBoardPresenter {
    private final HistoryInteractor history;
    private final HistoryViewModel model;
    public HistoryBoardPresenter(HistoryInteractor history, HistoryViewModel model) {
        this.history = Objects.requireNonNull(history);
        this.model = Objects.requireNonNull(model);
        history.onChange(() -> SwingUtilities.invokeLater(this::refresh));
    }
    public void refresh() {
        try {
            // Earlier versions accidentally auto-saved every partial recognition.
            // Keep those legacy files intact but do not display unconfirmed previews.
            List<HistoryItem> items = history.list().stream()
                    .filter(entry -> entry.kind() == HistoryEntry.Kind.CALCULATION
                            || entry.kind() == HistoryEntry.Kind.GRAPH)
                    .map(HistoryItem::new).toList();
            model.setItems(items);
        } catch (IOException e) {
            System.err.println("Math Lab history load failed: " + e.getMessage());
            model.setItems(List.of());
        }
    }
    public boolean undoDelete() {
        try { return history.undoDelete(); }
        catch (IOException e) { System.err.println("Math Lab history restore failed: " + e.getMessage()); return false; }
    }
    public boolean delete(String id) {
        try { return history.delete(id); }
        catch (IOException e) { System.err.println("Math Lab history delete failed: " + e.getMessage()); return false; }
    }
}
