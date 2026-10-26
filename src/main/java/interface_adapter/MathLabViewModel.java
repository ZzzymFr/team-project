package interface_adapter;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class MathLabViewModel {
    public static final String EXPRESSION = "expression";

    public static final String RESULT = "result";

    private final MathLabState state = new MathLabState();
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);

    public MathLabState getState() {
        return state;
    }

    public void firePropertyChanged(String propertyName) {
        support.firePropertyChange(propertyName, null, state);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }
}
