package use_case.evaluate;

import java.util.List;

// a calculated value as plain data, so presenter never sees entities
public final class ValueOutputData {
    public enum Kind { NUMBER, BOOLEAN, SET, VECTOR, NUMBER_SET }

    // exact value, every digit is real
    public static final int ALL_DIGITS = Integer.MAX_VALUE;

    private final Kind kind;
    private final List<Double> numbers;
    private final boolean truth;
    private final int significantDigits;

    public ValueOutputData(Kind kind, List<Double> numbers, boolean truth, int significantDigits) {
        this.kind = kind;
        this.numbers = numbers;
        this.truth = truth;
        this.significantDigits = significantDigits;
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

    // approx answers only know this many
    public int getSignificantDigits() {
        return significantDigits;
    }
}
