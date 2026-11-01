package use_case.evaluate;

import entity.Interval;
import entity.value.BooleanValue;
import entity.value.FiniteSetValue;
import entity.value.NumberSetValue;
import entity.value.NumberValue;
import entity.value.Value;
import entity.value.ValueVisitor;
import entity.value.VectorValue;

import java.util.List;

// entities -> output data, the only spot that knows both
public final class OutputDataMapper {
    private static final ValueVisitor<ValueOutputData> FROM_VALUE = new FromValue();

    private OutputDataMapper() {
    }

    public static ValueOutputData of(Value value) {
        return value.accept(FROM_VALUE);
    }

    public static IntervalOutputData of(Interval interval) {
        return new IntervalOutputData(interval.getLow(), interval.isLowIncluded(), interval.getHigh(),
                interval.isHighIncluded(), interval.isPoint());
    }

    private static final class FromValue implements ValueVisitor<ValueOutputData> {
        @Override
        public ValueOutputData visitNumber(NumberValue value) {
            return new ValueOutputData(ValueOutputData.Kind.NUMBER, List.of(value.getValue()), false);
        }

        @Override
        public ValueOutputData visitBoolean(BooleanValue value) {
            return new ValueOutputData(ValueOutputData.Kind.BOOLEAN, List.of(), value.getValue());
        }

        @Override
        public ValueOutputData visitFiniteSet(FiniteSetValue value) {
            return new ValueOutputData(ValueOutputData.Kind.SET, value.getElements(), false);
        }

        @Override
        public ValueOutputData visitVector(VectorValue value) {
            return new ValueOutputData(ValueOutputData.Kind.VECTOR, value.getComponents(), false);
        }

        @Override
        public ValueOutputData visitNumberSet(NumberSetValue value) {
            return new ValueOutputData(ValueOutputData.Kind.NUMBER_SET, List.of(), false);
        }
    }
}
