package entity.calculus;

import entity.UncalculableException;

import java.util.function.DoubleUnaryOperator;

public final class DerivativeCalculator {
    public static final int MAX_ORDER = 4;

    private static final int LEVELS = 12;
    private static final double SHRINK = 1.4;
    private static final double SAFE = 2.0;
    private static final double ONE_SIDED_TOLERANCE = 1e-4;
    private static final double ROUND_OFF = 1e-14;
    private static final double NOISE_FACTOR = 10;
    private static final double CONVERGED = 1e-9;
    private static final double MACHINE_EPSILON = 2.2e-16;
    private static final double[] STENCIL_WEIGHT = {0, 1, 4, 3, 16};
    // small steps for poles, big ones when f is huge vs slope
    private static final double[] OTHER_STARTS = {0.1, 0.01, 10, 100};

    private DerivativeCalculator() {
    }

    public static double derivative(DoubleUnaryOperator f, double x, int order) {
        if (order < 1 || order > MAX_ORDER) {
            throw new UncalculableException();
        }
        if (Double.isNaN(x) || Double.isInfinite(x)) {
            return Double.NaN;
        }
        double step = Math.max(0.1, Math.abs(x) * 1e-3);
        double[] best = extrapolate(f, x, order, step);
        for (int i = 0; i < OTHER_STARTS.length && !isConverged(best); i++) {
            double[] other = extrapolate(f, x, order, step * OTHER_STARTS[i]);
            boolean better = relativeError(other) < relativeError(best);
            if (better || (Double.isNaN(best[0]) && !Double.isNaN(other[0]))) {
                best = other;
            }
        }
        double result = best[0];
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            return Double.NaN;
        }
        if (order == 1 && !sidesAgree(f, x, result)) {
            return Double.NaN;
        }
        if (isFinite(best[1]) && Math.abs(result) <= NOISE_FACTOR * best[1]) {
            return 0;
        }
        return result;
    }

    private static boolean isConverged(double[] estimate) {
        return relativeError(estimate) <= CONVERGED;
    }

    private static double relativeError(double[] estimate) {
        return estimate[1] / Math.max(1, Math.abs(estimate[0]));
    }

    // ridders: shrink steps + extrapolate to 0
    private static double[] extrapolate(DoubleUnaryOperator f, double x, int order, double firstStep) {
        double h = firstStep;
        double first = centralDifference(f, x, h, order);
        int tries = 0;
        while (!isFinite(first) && tries < 20) {
            h = h / SHRINK;
            first = centralDifference(f, x, h, order);
            tries++;
        }
        if (!isFinite(first)) {
            return new double[] {Double.NaN, Double.POSITIVE_INFINITY};
        }
        double value = Math.abs(f.applyAsDouble(x));
        double size = isFinite(value) ? value : 0;
        double[][] table = new double[LEVELS][LEVELS];
        table[0][0] = first;
        double answer = first;
        double error = Double.POSITIVE_INFINITY;
        for (int i = 1; i < LEVELS; i++) {
            h = h / SHRINK;
            table[0][i] = centralDifference(f, x, h, order);
            if (!isFinite(table[0][i])) {
                break;
            }
            // cant beat the roundoff in f
            double floor = MACHINE_EPSILON * STENCIL_WEIGHT[order] * size / Math.pow(h, order);
            double factor = SHRINK * SHRINK;
            for (int j = 1; j <= i; j++) {
                table[j][i] = (table[j - 1][i] * factor - table[j - 1][i - 1]) / (factor - 1);
                factor = factor * SHRINK * SHRINK;
                double estimate = Math.max(floor, Math.max(Math.abs(table[j][i] - table[j - 1][i]),
                        Math.abs(table[j][i] - table[j - 1][i - 1])));
                if (estimate <= error) {
                    error = estimate;
                    answer = table[j][i];
                }
            }
            if (Math.abs(table[i][i] - table[i - 1][i - 1]) >= SAFE * error) {
                break;
            }
        }
        return new double[] {answer, error};
    }

    private static double centralDifference(DoubleUnaryOperator f, double x, double h, int order) {
        switch (order) {
            case 1:
                return (f.applyAsDouble(x + h) - f.applyAsDouble(x - h)) / (2 * h);
            case 2:
                return (f.applyAsDouble(x + h) - 2 * f.applyAsDouble(x) + f.applyAsDouble(x - h)) / (h * h);
            case 3:
                return (f.applyAsDouble(x + 2 * h) - 2 * f.applyAsDouble(x + h)
                        + 2 * f.applyAsDouble(x - h) - f.applyAsDouble(x - 2 * h)) / (2 * h * h * h);
            default:
                return (f.applyAsDouble(x + 2 * h) - 4 * f.applyAsDouble(x + h) + 6 * f.applyAsDouble(x)
                        - 4 * f.applyAsDouble(x - h) + f.applyAsDouble(x - 2 * h)) / (h * h * h * h);
        }
    }

    private static boolean sidesAgree(DoubleUnaryOperator f, double x, double derivative) {
        double h = 1e-6 * Math.max(1, Math.abs(x));
        double here = f.applyAsDouble(x);
        double right1 = f.applyAsDouble(x + h);
        double right2 = f.applyAsDouble(x + 2 * h);
        double left1 = f.applyAsDouble(x - h);
        double left2 = f.applyAsDouble(x - 2 * h);
        double right = (-3 * here + 4 * right1 - right2) / (2 * h);
        double left = (3 * here - 4 * left1 + left2) / (2 * h);
        if (!isFinite(right) || !isFinite(left)) {
            return false;
        }
        double size = Math.max(Math.max(Math.abs(here), Math.abs(right1)),
                Math.max(Math.abs(right2), Math.max(Math.abs(left1), Math.abs(left2))));
        // roundoff gets blown up by 1/h
        double noise = ROUND_OFF * size / h;
        return Math.abs(right - left) <= ONE_SIDED_TOLERANCE * Math.max(1, Math.abs(derivative)) + noise;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
