package use_case.evaluate;

import java.util.List;

// a calculated value as plain data, so presenter never sees entities
public final class ValueOutputData {
    public enum Kind { NUMBER, BOOLEAN, SET, VECTOR, NUMBER_SET }

    private final Kind kind;
    private final List<Double> numbers;
    private final boolean truth;

    public ValueOutputData(Kind kind, List<Double> numbers, boolean truth) {
        this.kind = kind;
        this.numbers = numbers;
        this.truth = truth;
    }

    public Kind getKind() {
        return kind;
    }

    // one entry for a number, all of them for sets + vectors
    public List<Double> getNumbers() {
        return numbers;
    }

    public boolean isTrue() {
        return truth;
    }
}
