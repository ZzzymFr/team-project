package entity.value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class FiniteSetValue extends Value {
    private final List<Double> elements;

    public FiniteSetValue(Collection<Double> elements) {
        List<Double> sorted = new ArrayList<>(elements);
        Collections.sort(sorted);
        List<Double> distinct = new ArrayList<>();
        for (double element : sorted) {
            if (distinct.isEmpty() || !Values.areAlmostEqual(distinct.get(distinct.size() - 1), element)) {
                distinct.add(element);
            }
        }
        this.elements = Collections.unmodifiableList(distinct);
    }

    public List<Double> getElements() {
        return elements;
    }
}
