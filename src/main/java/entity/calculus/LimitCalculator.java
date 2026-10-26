package entity.calculus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

public final class LimitCalculator {
    private static final int SAMPLES = 30;
    private static final int MIN_SAMPLES = 5;
    private static final int LEADING_SKIPS = 10;
    private static final double FIRST_STEP = 0.1;
    private static final double SHRINK = 2;
    private static final int RICHARDSON_COLUMNS = 10;
    private static final int WYNN_COLUMNS = 8;
    private static final double SETTLED = 1e-14;
    private static final double ACCEPTED = 1e-8;
    private static final double SIDES_AGREE = 1e-7;
    private static final double NOISE_FACTOR = 10;
    private static final double SAFE = 2;
    private static final int NOISY_ROWS = 2;
    private static final int GROWTH_WINDOW = 6;
    private static final double STEADY_GROWTH = 0.98;

    private LimitCalculator() {
    }

    // side: -1 below, +1 above, 0 both. NaN = no limit
    public static double limit(DoubleUnaryOperator f, double target, int side) {
        if (Double.isNaN(target)) {
            return Double.NaN;
        }
        if (target == Double.POSITIVE_INFINITY) {
            return oneSided(t -> f.applyAsDouble(1 / t));
        }
        if (target == Double.NEGATIVE_INFINITY) {
            return oneSided(t -> f.applyAsDouble(-1 / t));
        }
        double scale = Math.max(1, Math.abs(target));
        DoubleUnaryOperator above = h -> f.applyAsDouble(target + h * scale);
        DoubleUnaryOperator below = h -> f.applyAsDouble(target - h * scale);
        if (side > 0) {
            return oneSided(above);
        }
        if (side < 0) {
            return oneSided(below);
        }
        double left = oneSided(below);
        if (Double.isNaN(left)) {
            return Double.NaN;
        }
        return combine(left, oneSided(above));
    }

    private static double combine(double left, double right) {
        if (Double.isNaN(right)) {
            return Double.NaN;
        }
        if (left == right) {
            return left;
        }
        if (Double.isInfinite(left) || Double.isInfinite(right)) {
            return Double.NaN;
        }
        double size = Math.max(1, Math.max(Math.abs(left), Math.abs(right)));
        if (Math.abs(left - right) <= SIDES_AGREE * size) {
            return (left + right) / 2;
        }
        return Double.NaN;
    }

    // limit of g(h) as h->0 from halving samples
    private static double oneSided(DoubleUnaryOperator g) {
        double h = FIRST_STEP;
        double y = g.applyAsDouble(h);
        for (int i = 0; i < LEADING_SKIPS && !isFinite(y); i++) {
            h = h / SHRINK;
            y = g.applyAsDouble(h);
        }
        List<Double> samples = new ArrayList<>();
        Richardson richardson = new Richardson();
        while (true) {
            if (Double.isNaN(y)) {
                break;
            }
            if (Double.isInfinite(y)) {
                return towardsInfinity(samples, y);
            }
            samples.add(y);
            richardson.add(y);
            boolean done = richardson.accuracy() <= SETTLED
                    || (richardson.accuracy() <= ACCEPTED && richardson.worseRows >= NOISY_ROWS);
            if (samples.size() >= MIN_SAMPLES && done) {
                return snapped(richardson.value, richardson.error);
            }
            if (samples.size() == SAMPLES) {
                break;
            }
            h = h / SHRINK;
            y = g.applyAsDouble(h);
        }
        if (samples.size() < MIN_SAMPLES) {
            return Double.NaN;
        }
        double growth = steadyGrowth(samples);
        if (growth != 0) {
            return growth * Double.POSITIVE_INFINITY;
        }
        if (richardson.accuracy() <= ACCEPTED) {
            return snapped(richardson.value, richardson.error);
        }
        double[] wynn = wynn(samples);
        if (relativeError(wynn[0], wynn[1]) <= ACCEPTED) {
            return snapped(wynn[0], wynn[1]);
        }
        return Double.NaN;
    }

    private static double towardsInfinity(List<Double> samples, double infinity) {
        int n = samples.size();
        if (n < 2 || Math.signum(samples.get(n - 1) - samples.get(n - 2)) == Math.signum(infinity)) {
            return infinity;
        }
        return Double.NaN;
    }

    // +1/-1 if samples keep moving same way, else 0
    private static double steadyGrowth(List<Double> samples) {
        int start = Math.max(0, samples.size() - GROWTH_WINDOW);
        double direction = 0;
        double previousStep = 0;
        for (int i = start + 1; i < samples.size(); i++) {
            double step = samples.get(i) - samples.get(i - 1);
            if (step == 0 || !isFinite(step)) {
                return 0;
            }
            if (direction == 0) {
                direction = Math.signum(step);
            } else if (Math.signum(step) != direction || Math.abs(step) < STEADY_GROWTH * Math.abs(previousStep)) {
                return 0;
            }
            previousStep = step;
        }
        return direction;
    }

    // wynn epsilon, handles fractional powers + h log h
    private static double[] wynn(List<Double> samples) {
        int n = samples.size();
        double[] older = new double[n + 1];
        double[] current = new double[n];
        for (int i = 0; i < n; i++) {
            current[i] = samples.get(i);
        }
        double bestValue = Double.NaN;
        double bestError = Double.POSITIVE_INFINITY;
        for (int k = 1; k <= WYNN_COLUMNS && current.length > 1; k++) {
            double[] next = new double[current.length - 1];
            for (int m = 0; m < next.length; m++) {
                next[m] = older[m + 1] + 1 / (current[m + 1] - current[m]);
            }
            older = current;
            current = next;
            if (k % 2 == 1) {
                continue;
            }
            for (int m = 2; m < current.length; m++) {
                double value = current[m];
                double error = Math.max(Math.abs(value - current[m - 1]), Math.abs(value - current[m - 2]));
                if (isFinite(value) && isFinite(error)
                        && relativeError(value, error) <= relativeError(bestValue, bestError)) {
                    bestValue = value;
                    bestError = error;
                }
            }
        }
        return new double[] {bestValue, bestError};
    }

    private static double snapped(double value, double error) {
        if (Math.abs(value) <= NOISE_FACTOR * error) {
            return 0;
        }
        return value;
    }

    private static double relativeError(double value, double error) {
        if (!isFinite(value) || !isFinite(error)) {
            return Double.POSITIVE_INFINITY;
        }
        return error / Math.max(1, Math.abs(value));
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    // richardson extrap, keep best entry, count bad rows
    private static final class Richardson {
        private final List<double[]> rows = new ArrayList<>();
        private double value = Double.NaN;
        private double error = Double.POSITIVE_INFINITY;
        private int worseRows = 0;
        private double rowValue;
        private double rowError;

        void add(double sample) {
            int i = rows.size();
            double[] row = new double[Math.min(i, RICHARDSON_COLUMNS) + 1];
            row[0] = sample;
            rowValue = Double.NaN;
            rowError = Double.POSITIVE_INFINITY;
            if (i >= 2) {
                consider(sample, Math.max(Math.abs(sample - rows.get(i - 1)[0]),
                        Math.abs(sample - rows.get(i - 2)[0])));
            }
            double factor = 1;
            for (int j = 1; j < row.length; j++) {
                double[] up = rows.get(i - 1);
                factor = factor * SHRINK;
                row[j] = row[j - 1] + (row[j - 1] - up[j - 1]) / (factor - 1);
                double estimate = Math.max(Math.abs(row[j] - row[j - 1]), Math.abs(row[j] - up[j - 1]));
                if (j < up.length) {
                    estimate = Math.max(estimate, Math.abs(row[j] - up[j]));
                }
                consider(row[j], estimate);
            }
            rows.add(row);
            double rowAccuracy = relativeError(rowValue, rowError);
            if (rowAccuracy > SAFE * accuracy()) {
                worseRows++;
            } else {
                worseRows = 0;
            }
            if (rowAccuracy <= accuracy()) {
                value = rowValue;
                error = rowError;
            }
        }

        private void consider(double candidate, double candidateError) {
            if (relativeError(candidate, candidateError) <= relativeError(rowValue, rowError)) {
                rowValue = candidate;
                rowError = candidateError;
            }
        }

        double accuracy() {
            return relativeError(value, error);
        }
    }
}
