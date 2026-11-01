package use_case.evaluate;

import entity.value.BooleanValue;
import entity.value.FiniteSetValue;
import entity.value.NumberSetValue;
import entity.value.NumberValue;
import entity.value.Value;
import entity.value.ValueVisitor;
import entity.value.VectorValue;

import java.util.List;

// a calculated value as plain data, so presenter never sees entities
public final class ValueOutputData {
    public enum Kind { NUMBER, BOOLEAN, SET, VECTOR, NUMBER_SET }

    private static final ValueVisitor<ValueOutputData> FROM_VALUE = new FromValue();

    private final Kind kind;
    private final List<Double> numbers;
    private final boolean truth;

    private ValueOutputData(Kind kind, List<Double> numbers, boolean truth) {
        this.kind = kind;
        this.numbers = numbers;
        this.truth = truth;
    }

    public static ValueOutputData of(Value value) {
        return value.accept(FROM_VALUE);
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

    private static final class FromValue implements ValueVisitor<ValueOutputData> {
        @Override
        public ValueOutputData visitNumber(NumberValue value) {
            return new ValueOutputData(Kind.NUMBER, List.of(value.getValue()), false);
        }

        @Override
        public ValueOutputData visitBoolean(BooleanValue value) {
            return new ValueOutputData(Kind.BOOLEAN, List.of(), value.getValue());
        }

        @Override
        public ValueOutputData visitFiniteSet(FiniteSetValue value) {
            return new ValueOutputData(Kind.SET, value.getElements(), false);
        }

        @Override
        public ValueOutputData visitVector(VectorValue value) {
            return new ValueOutputData(Kind.VECTOR, value.getComponents(), false);
        }

        @Override
        public ValueOutputData visitNumberSet(NumberSetValue value) {
            return new ValueOutputData(Kind.NUMBER_SET, List.of(), false);
        }
    }
}
