package use_case.evaluate;

import entity.Interval;
import entity.MathSyntaxException;
import entity.UncalculableException;
import entity.symbolic.Expr;
import entity.symbolic.Rational;
import entity.value.BooleanValue;
import entity.value.FiniteSetValue;
import entity.value.NumberSetValue;
import entity.value.NumberValue;
import entity.value.Value;
import entity.value.ValueVisitor;
import entity.value.VectorValue;

import java.util.ArrayList;
import java.util.List;

// entities -> output data, the only spot that knows both
public final class OutputDataMapper {
    private static final ValueVisitor<ValueOutputData> FROM_VALUE = new FromValue(ValueOutputData.ALL_DIGITS);

    private OutputDataMapper() {
    }

    public static ValueOutputData of(Value value) {
        return value.accept(FROM_VALUE);
    }

    public static ValueOutputData of(Value value, int significantDigits) {
        return value.accept(new FromValue(significantDigits));
    }

    public static IntervalOutputData of(Interval interval) {
        return new IntervalOutputData(interval.getLow(), interval.isLowIncluded(), interval.getHigh(),
                interval.isHighIncluded(), interval.isPoint());
    }

    public static InvalidOutputData of(MathSyntaxException e) {
        return new InvalidOutputData(e.getProblem().name(), e.getDetails());
    }

    public static UncalculableOutputData of(UncalculableException e) {
        return new UncalculableOutputData(e.getReason() == null ? null : e.getReason().name(), e.getDetail());
    }

    public static ExprOutputData of(Expr e) {
        Rational value = e.value();
        return new ExprOutputData(ExprOutputData.Kind.valueOf(e.kind().name()), value == null ? 0 : value.getNum(),
                value == null ? 0 : value.getDen(), e.isDecimal(), e.name(), terms(e),
                e.base() == null ? null : of(e.base()), e.exponent() == null ? null : of(e.exponent()));
    }

    private static List<ExprOutputData> terms(Expr e) {
        if (e.terms() == null) {
            return null;
        }
        List<ExprOutputData> terms = new ArrayList<>();
        for (Expr term : e.terms()) {
            terms.add(of(term));
        }
        return terms;
    }

    private static final class FromValue implements ValueVisitor<ValueOutputData> {
        private final int digits;

        private FromValue(int digits) {
            this.digits = digits;
        }

        @Override
        public ValueOutputData visitNumber(NumberValue value) {
            return new ValueOutputData(ValueOutputData.Kind.NUMBER, List.of(value.getValue()), false, digits);
        }

        @Override
        public ValueOutputData visitBoolean(BooleanValue value) {
            return new ValueOutputData(ValueOutputData.Kind.BOOLEAN, List.of(), value.getValue(), digits);
        }

        @Override
        public ValueOutputData visitFiniteSet(FiniteSetValue value) {
            return new ValueOutputData(ValueOutputData.Kind.SET, value.getElements(), false, digits);
        }

        @Override
        public ValueOutputData visitVector(VectorValue value) {
            return new ValueOutputData(ValueOutputData.Kind.VECTOR, value.getComponents(), false, digits);
        }

        @Override
        public ValueOutputData visitNumberSet(NumberSetValue value) {
            return new ValueOutputData(ValueOutputData.Kind.NUMBER_SET, List.of(), false, digits);
        }
    }
}
