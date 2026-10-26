package interface_adapter.graph;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * Holds the {@link GraphState} and notifies the view when it changes.
 */
public class GraphViewModel {
    public static final String STATE_PROPERTY = "graphState";

    private final PropertyChangeSupport support = new PropertyChangeSupport(this);
    private GraphState state = new GraphState();

    public GraphState getState() {
        return state;
    }

    public void setState(GraphState state) {
        this.state = state;
    }

    public void firePropertyChanged() {
        support.firePropertyChange(STATE_PROPERTY, null, state);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }
}
