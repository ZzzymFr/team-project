package interface_adapter.history;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Collections;
import java.util.List;

// rows for the history panel, newest first
public class HistoryViewModel {
    public static final String ITEMS_PROPERTY = "items";

    private final PropertyChangeSupport support = new PropertyChangeSupport(this);
    private List<HistoryItem> items = List.of();

    public List<HistoryItem> getItems() {
        return items;
    }

    public void setItems(List<HistoryItem> items) {
        List<HistoryItem> old = this.items;
        this.items = Collections.unmodifiableList(items);
        support.firePropertyChange(ITEMS_PROPERTY, old, this.items);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }
}
