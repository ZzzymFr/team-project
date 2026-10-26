package entity.value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class VectorValue extends Value {
    private final List<Double> components;

    public VectorValue(List<Double> components) {
        this.components = Collections.unmodifiableList(new ArrayList<>(components));
    }

    public List<Double> getComponents() {
        return components;
    }

    public int getDimension() {
        return components.size();
    }
}
