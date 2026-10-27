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
    private static final double[] OTHER_STARTS = {0.1, 0.01, 10, 100};
    private static final double[] SMALL_STARTS = {1, 0.1, 1e-2, 1e-3, 1e-4};
    private static final double MIN_STEP = 1e-12;
    private static final double MAX_ERROR = 1e-6;
    private static final double LARGE_START = 1;
    private static final double REFERENCE_MARGIN = 3;
    private static final double SHRINK_START = 10;
    private static final int CHECKS = 3;
    private static final double CHECK_SHRINK = 10;
    private static final double CORNER_STEP = 1e-5;
    private static final double CORNER_SHRINK = 1000;

    private DerivativeCalculator() {
    }

    public static double derivative(DoubleUnaryOperator f, double x, int order) {
        return derivativeWithError(f, x, order)[0];
    }

    // {deriv, rel error}
    public static double[] derivativeWithError(DoubleUnaryOperator f, double x, int order) {
        if (order < 1 || order > MAX_ORDER) {
            throw new UncalculableException();
        }
        if (Double.isNaN(x) || Double.isInfinite(x)) {
            return new double[] {Double.NaN, 0};
        }
        double step = Math.max(0.1, Math.abs(x) * 1e-3);
        double[] best = extrapolate(f, x, order, step);
        double[] starts = otherStarts(step, x);
        for (int i = 0; i < starts.length && !isConverged(best); i++) {
            best = better(best, extrapolate(f, x, order, starts[i]));
        }
        // big steps can agree by luck, check w/ small ones
        if (best[3] > LARGE_START) {
            double[] reference = extrapolate(f, x, order, LARGE_START * SMALL_STARTS[1] * scaleOf(x));
            double margin = REFERENCE_MARGIN * (best[1] + reference[1]);
            if (isFinite(reference[0]) && !(Math.abs(reference[0] - best[0]) <= margin)) {
                best = reference;
            }
        }
        if (order == 1) {
            best = checkedFirstDerivative(f, x, best);
        }
        double result = best[0];
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            return new double[] {Double.NaN, 0};
        }
        double error = best[1] / Math.max(1, Math.abs(result));
        // too far out, x rounding eats the digits
        if (!(error <= MAX_ERROR)) {
            throw new UncalculableException();
        }
        if (Math.abs(result) <= NOISE_FACTOR * best[1]) {
            return new double[] {0, error};
        }
        return new double[] {result, error};
    }

    // pick first step size based on f + x
    private static double[] otherStarts(double step, double x) {
        double[] starts = new double[OTHER_STARTS.length + SMALL_STARTS.length];
        for (int i = 0; i < OTHER_STARTS.length; i++) {
            starts[i] = step * OTHER_STARTS[i];
        }
        for (int i = 0; i < SMALL_STARTS.length; i++) {
            starts[OTHER_STARTS.length + i] = scaleOf(x) * SMALL_STARTS[i];
        }
        return starts;
    }

    private static double scaleOf(double x) {
        return x == 0 ? 1 : Math.min(1, Math.abs(x));
    }

    private static double[] better(double[] best, double[] other) {
        if (relativeError(other) < relativeError(best) || (Double.isNaN(best[0]) && !Double.isNaN(other[0]))) {
            return other;
        }
        return best;
    }

    // check deriv vs left/right slopes, else corner/edge
    private static double[] checkedFirstDerivative(DoubleUnaryOperator f, double x, double[] estimate) {
        double[] best = estimate;
        double floor = MIN_STEP * Math.max(Math.abs(x), Double.MIN_NORMAL);
        double start = isFinite(best[2]) ? best[2] : Math.max(0.1, Math.abs(x) * 1e-3);
        while (true) {
            if (isFinite(best[0]) && matchesSides(f, x, best[0], best[2])) {
                return best;
            }
            start = start / SHRINK_START;
            if (start < floor) {
                break;
            }
            best = extrapolate(f, x, 1, start);
        }
        if (isCorner(f, x)) {
            return new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NaN, Double.NaN};
        }
        throw new UncalculableException();
    }

    // one sided slopes at smaller steps
    private static boolean matchesSides(DoubleUnaryOperator f, double x, double derivative, double step) {
        double h = isFinite(step) ? step : 1e-6 * Math.max(1, Math.abs(x));
        for (int i = 0; i < CHECKS; i++) {
            h = h / CHECK_SHRINK;
            double[] sides = oneSidedSlopes(f, x, h);
            if (sides == null) {
                continue;
            }
            double tolerance = ONE_SIDED_TOLERANCE * Math.max(1, Math.abs(derivative)) + sides[2];
            if (Math.abs(sides[0] - derivative) <= tolerance && Math.abs(sides[1] - derivative) <= tolerance) {
                return true;
            }
        }
        return false;
    }

    // corner: slopes differ both sides
    private static boolean isCorner(DoubleUnaryOperator f, double x) {
        double h = CORNER_STEP * Math.max(1, Math.abs(x));
        double[] wide = oneSidedSlopes(f, x, h);
        double[] narrow = oneSidedSlopes(f, x, h / CORNER_SHRINK);
        if (wide == null || narrow == null) {
            return true;
        }
        double gapWide = Math.abs(wide[0] - wide[1]);
        double gapNarrow = Math.abs(narrow[0] - narrow[1]);
        double size = Math.max(1, Math.max(Math.abs(narrow[0]), Math.abs(narrow[1])));
        return gapNarrow > ONE_SIDED_TOLERANCE * size + narrow[2] && gapNarrow > gapWide / 2
                && gapNarrow < 2 * gapWide;
    }

    // {right, left, roundoff} or null
    private static double[] oneSidedSlopes(DoubleUnaryOperator f, double x, double h) {
        double here = f.applyAsDouble(x);
        double right1 = f.applyAsDouble(x + h);
        double right2 = f.applyAsDouble(x + 2 * h);
        double left1 = f.applyAsDouble(x - h);
        double left2 = f.applyAsDouble(x - 2 * h);
        double right = (-3 * here + 4 * right1 - right2) / (2 * h);
        double left = (3 * here - 4 * left1 + left2) / (2 * h);
        if (!isFinite(right) || !isFinite(left)) {
            return null;
        }
        double size = Math.max(Math.max(Math.abs(here), Math.abs(right1)),
                Math.max(Math.abs(right2), Math.max(Math.abs(left1), Math.abs(left2))));
        // roundoff in f and x blown up by 1/h
        double slope = Math.max(Math.abs(right), Math.abs(left));
        double noise = (ROUND_OFF * size + ROUND_OFF * Math.abs(x) * slope) / h;
        return new double[] {right, left, noise};
    }

    private static boolean isConverged(double[] estimate) {
        return relativeError(estimate) <= CONVERGED;
    }

    private static double relativeError(double[] estimate) {
        return estimate[1] / Math.max(1, Math.abs(estimate[0]));
    }

    // ridders, returns {deriv, err, min step, first step}
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
            return new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NaN, firstStep};
        }
        double value = Math.abs(f.applyAsDouble(x));
        double size = isFinite(value) ? value : 0;
        // x+h rounds too
        double slope = Math.abs(order == 1 ? first : centralDifference(f, x, h, 1));
        if (isFinite(slope)) {
            size = size + Math.abs(x) * slope;
        }
        double[][] table = new double[LEVELS][LEVELS];
        table[0][0] = first;
        double answer = first;
        double answerStep = h;
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
                    answerStep = h;
                }
            }
            if (Math.abs(table[i][i] - table[i - 1][i - 1]) >= SAFE * error) {
                break;
            }
        }
        return new double[] {answer, error, answerStep, firstStep};
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

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
