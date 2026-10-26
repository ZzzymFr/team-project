package entity.calculus;

import entity.UncalculableException;

import java.util.function.DoubleUnaryOperator;

public final class DerivativeCalculator {
    public static final int MAX_ORDER = 4;

    private static final int SIGNIFICANT_DIGITS = 9;
    private static final int LEVELS = 12;
    private static final double SHRINK = 1.4;
    private static final double SAFE = 2.0;
    private static final double ONE_SIDED_TOLERANCE = 1e-4;
    private static final double ZERO_TOLERANCE = 1e-6;
    private static final double CONVERGED = 1e-9;

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
        for (int start = 1; start <= 2 && !isConverged(best); start++) {
            step = step / 10;
            double[] smaller = extrapolate(f, x, order, step);
            if (smaller[1] < best[1] || (Double.isNaN(best[0]) && !Double.isNaN(smaller[0]))) {
                best = smaller;
            }
        }
        double result = best[0];
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            return Double.NaN;
        }
        if (order == 1 && !sidesAgree(f, x, result)) {
            return Double.NaN;
        }
        double value = f.applyAsDouble(x);
        double scale = Double.isNaN(value) || Double.isInfinite(value) ? 1 : Math.max(1, Math.abs(value));
        if (Math.abs(result) < ZERO_TOLERANCE * scale) {
            return 0;
        }
        return Rounding.toSignificant(result, SIGNIFICANT_DIGITS);
    }

    private static boolean isConverged(double[] estimate) {
        return estimate[1] <= CONVERGED * Math.max(1, Math.abs(estimate[0]));
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
            double factor = SHRINK * SHRINK;
            for (int j = 1; j <= i; j++) {
                table[j][i] = (table[j - 1][i] * factor - table[j - 1][i - 1]) / (factor - 1);
                factor = factor * SHRINK * SHRINK;
                double estimate = Math.max(Math.abs(table[j][i] - table[j - 1][i]),
                        Math.abs(table[j][i] - table[j - 1][i - 1]));
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
        double right = (-3 * here + 4 * f.applyAsDouble(x + h) - f.applyAsDouble(x + 2 * h)) / (2 * h);
        double left = (3 * here - 4 * f.applyAsDouble(x - h) + f.applyAsDouble(x - 2 * h)) / (2 * h);
        if (!isFinite(right) || !isFinite(left)) {
            return false;
        }
        return Math.abs(right - left) <= ONE_SIDED_TOLERANCE * Math.max(1, Math.abs(derivative));
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
