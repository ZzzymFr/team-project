package use_case.evaluate;

import entity.value.BooleanValue;
import entity.value.FiniteSetValue;
import entity.value.NumberSetValue;
import entity.value.NumberValue;
import entity.value.ValueVisitor;
import entity.value.VectorValue;

import java.util.List;

// digits an approx answer really has, all if the 9 sig cut leaves the whole part alone
final class ApproximateDigits implements ValueVisitor<Integer> {
    @Override
    public Integer visitNumber(NumberValue value) {
        return digits(List.of(value.getValue()));
    }

    @Override
    public Integer visitBoolean(BooleanValue value) {
        return ValueOutputData.ALL_DIGITS;
    }

    @Override
    public Integer visitFiniteSet(FiniteSetValue value) {
        return digits(value.getElements());
    }

    @Override
    public Integer visitVector(VectorValue value) {
        return digits(value.getComponents());
    }

    @Override
    public Integer visitNumberSet(NumberSetValue value) {
        return ValueOutputData.ALL_DIGITS;
    }

    private static int digits(List<Double> numbers) {
        for (double number : numbers) {
            if (Math.abs(number - ValueRounder.round(number)) >= 0.5) {
                return ValueRounder.APPROXIMATE_DIGITS;
            }
        }
        return ValueOutputData.ALL_DIGITS;
    }
}
