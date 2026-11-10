package entity.calculus;

import java.util.function.DoubleUnaryOperator;

// one side of a limit as g(h), h->0
final class LimitSide {
    private final DoubleUnaryOperator value;
    private final DoubleUnaryOperator noise;
    private final double reach;

    // reach = smallest h worth sampling
    LimitSide(DoubleUnaryOperator value, DoubleUnaryOperator noise, double reach) {
        this.value = value;
        this.noise = noise;
        this.reach = reach;
    }

    double valueAt(double h) {
        return value.applyAsDouble(h);
    }

    double noiseAt(double h) {
        return noise.applyAsDouble(h);
    }

    boolean reaches(double h) {
        return h >= reach;
    }
}
