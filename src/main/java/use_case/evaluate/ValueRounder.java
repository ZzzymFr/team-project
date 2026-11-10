package use_case.evaluate;

import entity.calculus.Rounding;
import entity.value.BooleanValue;
import entity.value.FiniteSetValue;
import entity.value.NumberSetValue;
import entity.value.NumberValue;
import entity.value.Value;
import entity.value.ValueVisitor;
import entity.value.VectorValue;

import java.util.ArrayList;
import java.util.List;

// approx answers cut to 9 sig so noise doesnt show
final class ValueRounder implements ValueVisitor<Value> {
    static final int APPROXIMATE_DIGITS = 9;

    @Override
    public Value visitNumber(NumberValue value) {
        return new NumberValue(round(value.getValue()));
    }

    @Override
    public Value visitBoolean(BooleanValue value) {
        return value;
    }

    @Override
    public Value visitFiniteSet(FiniteSetValue value) {
        return new FiniteSetValue(roundAll(value.getElements()));
    }

    @Override
    public Value visitVector(VectorValue value) {
        return new VectorValue(roundAll(value.getComponents()));
    }

    @Override
    public Value visitNumberSet(NumberSetValue value) {
        return value;
    }

    private static List<Double> roundAll(List<Double> numbers) {
        List<Double> rounded = new ArrayList<>();
        for (double number : numbers) {
            rounded.add(round(number));
        }
        return rounded;
    }

    static double round(double number) {
        return Rounding.toSignificant(number, APPROXIMATE_DIGITS);
    }
}
