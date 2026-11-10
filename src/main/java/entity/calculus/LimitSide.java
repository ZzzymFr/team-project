package entity.calculus;

import java.util.function.DoublePredicate;
import java.util.function.DoubleUnaryOperator;

// one side of a limit as g(h), h->0
final class LimitSide {
    private final DoubleUnaryOperator value;
    private final DoubleUnaryOperator noise;
    private final DoublePredicate absorbed;
    private final double reach;
    private boolean lostNaN;

    // reach = smallest h worth sampling
    LimitSide(DoubleUnaryOperator value, DoubleUnaryOperator noise, DoublePredicate absorbed, double reach) {
        this.value = value;
        this.noise = noise;
        this.absorbed = absorbed;
        this.reach = reach;
    }

    double valueAt(double h) {
        return value.applyAsDouble(h);
    }

    double noiseAt(double h) {
        return noise.applyAsDouble(h);
    }

    // rounding swallowed part of the sample at h
    boolean absorbedAt(double h) {
        return absorbed.test(h);
    }

    // inf-inf in a normal-run sample, so a NaN answer isnt a real "no limit"
    void noteLostNaN() {
        lostNaN = true;
    }

    boolean lostNaN() {
        return lostNaN;
    }

    boolean reaches(double h) {
        return h >= reach;
    }
}
