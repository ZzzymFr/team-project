package entity.calculus;

import entity.UncalculableException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

public final class LimitCalculator {
    private static final int SAMPLES = 30;
    private static final int MIN_SAMPLES = 5;
    private static final int POSITIONS = 40;
    private static final int TAIL = 3;
    private static final int OSCILLATION_TURNS = 3;
    // not a round number so poles dont land on samples
    private static final double FIRST_STEP = 0.0937;
    private static final double SHRINK = 2;
    private static final int RICHARDSON_COLUMNS = 10;
    private static final int WYNN_COLUMNS = 8;
    private static final double SETTLED = 1e-14;
    private static final double ACCEPTED = 1e-8;
    private static final double SIDES_AGREE = 1e-7;
    private static final double NOISE_FACTOR = 10;
    private static final double ROUND_OFF = 1e-12;
    private static final double SAFE = 2;
    private static final int NOISY_ROWS = 2;
    private static final int GROWTH_WINDOW = 6;
    private static final double STEADY_GROWTH = 0.999;
    private static final double ACCELERATION = 1.5;
    private static final double SWING_SHRINK = 0.5;

    private LimitCalculator() {
    }

    // side -1/+1/0, NaN = no limit, uncalc = cant tell
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

    // limit of g(h) as h->0, NaN sample restarts the run
    private static double oneSided(DoubleUnaryOperator g) {
        double h = FIRST_STEP;
        List<Double> samples = new ArrayList<>();
        List<Double> before = new ArrayList<>();
        Richardson richardson = new Richardson();
        boolean anyFinite = false;
        int badRun = 0;
        double badValue = Double.NaN;
        for (int k = 0; k < POSITIONS && samples.size() < SAMPLES; k++) {
            double y = g.applyAsDouble(h);
            h = h / SHRINK;
            if (!isFinite(y)) {
                if (!samples.isEmpty()) {
                    before = samples;
                    samples = new ArrayList<>();
                    richardson = new Richardson();
                }
                badValue = badRun == 0 || y == badValue ? y : Double.NaN;
                badRun++;
                continue;
            }
            anyFinite = true;
            badRun = 0;
            samples.add(y);
            richardson.add(y);
            if (samples.size() >= MIN_SAMPLES && isSettled(samples, richardson)) {
                return snapped(richardson.value, richardson.error, y);
            }
        }
        if (!anyFinite) {
            // all samples same inf or side outside domain
            return Double.isInfinite(badValue) ? badValue : Double.NaN;
        }
        if (samples.size() >= MIN_SAMPLES) {
            return fromSamples(samples, richardson);
        }
        if (badRun >= TAIL && Double.isInfinite(badValue) && movesTowards(before, badValue)) {
            return badValue;
        }
        throw new UncalculableException();
    }

    // small rel error not enough, oscillating goes to fromSamples
    private static boolean isSettled(List<Double> samples, Richardson richardson) {
        List<Double> recent = samples.subList(Math.max(0, samples.size() - GROWTH_WINDOW), samples.size());
        if (steadyGrowth(samples) != 0 || turns(recent) > 0) {
            return false;
        }
        boolean exact = richardson.accuracy() <= SETTLED && stepsDoNotGrow(samples);
        boolean noisy = richardson.accuracy() <= ACCEPTED && richardson.worseRows >= NOISY_ROWS;
        return exact || noisy;
    }

    private static boolean stepsDoNotGrow(List<Double> samples) {
        int n = samples.size();
        for (int i = n - MIN_SAMPLES + 2; i < n; i++) {
            double step = Math.abs(samples.get(i) - samples.get(i - 1));
            if (step > Math.abs(samples.get(i - 1) - samples.get(i - 2))) {
                return false;
            }
        }
        return true;
    }

    private static double fromSamples(List<Double> samples, Richardson richardson) {
        double growth = steadyGrowth(samples);
        if (growth != 0) {
            // slow growth might level off, fast wont
            if (Math.signum(samples.get(samples.size() - 1)) != growth && !accelerates(samples)) {
                throw new UncalculableException();
            }
            return growth * Double.POSITIVE_INFINITY;
        }
        double nearest = samples.get(samples.size() - 1);
        if (turns(samples) >= OSCILLATION_TURNS) {
            return fromOscillation(samples, richardson, nearest);
        }
        if (richardson.accuracy() <= ACCEPTED) {
            return snapped(richardson.value, richardson.error, nearest);
        }
        double[] wynn = wynn(samples);
        if (relativeError(wynn[0], wynn[1]) <= ACCEPTED) {
            return snapped(wynn[0], wynn[1], nearest);
        }
        throw new UncalculableException();
    }

    // swing not shrinking = no limit
    private static double fromOscillation(List<Double> samples, Richardson richardson, double nearest) {
        int n = samples.size();
        List<Double> earlier = samples.subList(0, n / 2);
        List<Double> later = samples.subList(n / 2, n);
        double swing = max(later) - min(later);
        if (swing >= SWING_SHRINK * (max(earlier) - min(earlier))) {
            return Double.NaN;
        }
        double[] wynn = wynn(samples);
        double[][] candidates = {{richardson.value, richardson.error}, wynn};
        for (double[] candidate : candidates) {
            double value = candidate[0];
            double error = candidate[1];
            if (isFinite(value) && isFinite(error) && error <= ACCEPTED * Math.max(Math.abs(value), swing)
                    && value >= min(later) - error && value <= max(later) + error) {
                return snapped(value, error, nearest);
            }
        }
        // swing already closed in
        List<Double> last = samples.subList(n - MIN_SAMPLES, n);
        double middle = (max(last) + min(last)) / 2;
        double halfWidth = (max(last) - min(last)) / 2;
        if (relativeError(middle, halfWidth) <= ACCEPTED) {
            return snapped(middle, halfWidth, nearest);
        }
        throw new UncalculableException();
    }

    private static double max(List<Double> values) {
        double result = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            result = Math.max(result, value);
        }
        return result;
    }

    private static double min(List<Double> values) {
        double result = Double.POSITIVE_INFINITY;
        for (double value : values) {
            result = Math.min(result, value);
        }
        return result;
    }

    // overflowed going to that inf
    private static boolean movesTowards(List<Double> samples, double infinity) {
        int n = samples.size();
        if (n == 0 || Math.signum(samples.get(n - 1)) != Math.signum(infinity)) {
            return false;
        }
        return n == 1 || Math.signum(samples.get(n - 1) - samples.get(n - 2)) == Math.signum(infinity);
    }

    private static boolean accelerates(List<Double> samples) {
        int n = samples.size();
        double last = Math.abs(samples.get(n - 1) - samples.get(n - 2));
        return last >= ACCELERATION * Math.abs(samples.get(n - 2) - samples.get(n - 3));
    }

    // count direction changes, ignore roundoff steps
    private static int turns(List<Double> samples) {
        int turns = 0;
        double previousStep = 0;
        for (int i = 1; i < samples.size(); i++) {
            double step = samples.get(i) - samples.get(i - 1);
            if (Math.abs(step) <= ROUND_OFF * Math.max(1, Math.abs(samples.get(i)))) {
                continue;
            }
            if (step * previousStep < 0) {
                turns++;
            }
            previousStep = step;
        }
        return turns;
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

    // within error of 0 -> just 0
    private static double snapped(double value, double error, double nearestSample) {
        if (Math.abs(value) <= Math.max(NOISE_FACTOR * error, ROUND_OFF * Math.abs(nearestSample))) {
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
