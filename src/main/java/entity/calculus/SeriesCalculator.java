package entity.calculus;

import entity.UncalculableException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongToDoubleFunction;

public final class SeriesCalculator {
    public static final long MAX_TERMS = 1_000_000;

    private SeriesCalculator() {
    }

    // end==MAX_VALUE means inf. NaN no sum, uncalc cant tell
    public static double series(LongToDoubleFunction term, long start, long end, boolean product) {
        if (end == Long.MAX_VALUE) {
            return new InfiniteSeries(term, start, product).calculate();
        }
        if (start > end) {
            return product ? 1 : 0;
        }
        if (end - start < 0 || end - start >= MAX_TERMS) {
            throw new UncalculableException();
        }
        if (product) {
            double result = 1;
            for (long n = start; n <= end; n++) {
                double factor = term.applyAsDouble(n);
                if (Double.isNaN(factor)) {
                    return Double.NaN;
                }
                result = result * factor;
            }
            return result;
        }
        CompensatedSum sum = new CompensatedSum();
        for (long n = start; n <= end; n++) {
            double value = term.applyAsDouble(n);
            if (Double.isNaN(value)) {
                return Double.NaN;
            }
            sum.add(value);
        }
        return sum.value();
    }

    // neumaier summation
    private static final class CompensatedSum {
        private double total;
        private double compensation;

        void add(double value) {
            double next = total + value;
            if (Math.abs(total) >= Math.abs(value)) {
                compensation += (total - next) + value;
            } else {
                compensation += (value - next) + total;
            }
            total = next;
        }

        double value() {
            if (!isFinite(total)) {
                return total;
            }
            return total + compensation;
        }
    }

    // check partial sums at 2^k, product as sum of logs
    private static final class InfiniteSeries {
        private static final int LEVELS = 20;
        private static final int FIRST_ESTIMATE = 6;
        private static final int FIRST_TAIL = 6;
        private static final int FIRST_SAMPLE = 2;
        private static final double NEGLIGIBLE = 0x1p-56;
        private static final double ACCEPTED = 1e-10;
        private static final double AGREE = 1e-9;
        private static final double LATE_ACCEPTED = 1e-8;
        private static final double ROUND_OFF = 0x1p-50;
        private static final double SHRINKING_STEPS = 0.99;
        private static final int WINDOW = 5;
        private static final double STEADY = 1e-9;
        private static final double LEVELLING = 0.6;
        private static final double LEVELLED = 1e-4;
        private static final double NOT_SHRINKING = 1e-3;
        private static final int LOOK_AHEAD = 3;
        private static final int ALL_INFINITE = 64;
        private static final int RECENT = 4;
        private static final long LARGEST_START = 1 << 12;

        private final LongToDoubleFunction term;
        private final long start;
        private final boolean product;

        private final CompensatedSum sum = new CompensatedSum();
        private double sumOfSizes = 0;
        private double direct = 1;
        private int sign = 1;
        private long tinyFrom = -1;
        private final double[] recent = new double[RECENT];
        private int recentCount = 0;

        private final double[] checkpoint = new double[LEVELS + 1];
        private final double[] low = new double[LEVELS + 1];
        private final double[] high = new double[LEVELS + 1];
        private final double[] size = new double[LEVELS + 1];
        private final boolean[] negative = new boolean[LEVELS + 1];
        private final List<Double> samples = new ArrayList<>();
        private final Richardson richardson = new Richardson();
        private double[] previousEstimate = {Double.NaN, Double.POSITIVE_INFINITY};

        InfiniteSeries(LongToDoubleFunction term, long start, boolean product) {
            this.term = term;
            this.start = start;
            this.product = product;
            for (int k = 0; k <= LEVELS; k++) {
                low[k] = Double.POSITIVE_INFINITY;
                high[k] = Double.NEGATIVE_INFINITY;
            }
        }

        double calculate() {
            if (start > Long.MAX_VALUE - (2L << LEVELS)) {
                throw new UncalculableException();
            }
            for (long i = 0; i < 1L << LEVELS; i++) {
                double a = term.applyAsDouble(start + i);
                if (Double.isNaN(a)) {
                    return undefinedTerm(i);
                }
                if (product && a == 0) {
                    return 0;
                }
                if (Double.isInfinite(a)) {
                    return infiniteTerm(i, a);
                }
                double x = a;
                double deviation = Math.abs(a);
                if (product) {
                    x = Math.log(Math.abs(a));
                    deviation = Math.abs(a - 1);
                    direct = direct * a;
                    if (a < 0) {
                        sign = -sign;
                    }
                }
                sum.add(x);
                sumOfSizes += Math.abs(x);
                double value = sum.value();
                if (!isFinite(value)) {
                    // only sums get here, logs dont overflow
                    return overflow(a);
                }
                remember(a);
                boolean negligible = product ? deviation <= NEGLIGIBLE : deviation <= NEGLIGIBLE * Math.abs(value);
                if (!negligible) {
                    tinyFrom = -1;
                } else if (tinyFrom < 0) {
                    tinyFrom = i;
                }
                long count = i + 1;
                int level = count == 1 ? 0 : 64 - Long.numberOfLeadingZeros(count - 1);
                low[level] = Math.min(low[level], value);
                high[level] = Math.max(high[level], value);
                size[level] = Math.max(size[level], deviation);
                negative[level] = negative[level] || a < 0;
                if (count == 1L << level) {
                    checkpoint[level] = value;
                    if (level >= FIRST_TAIL && tinyFrom >= 0 && tinyFrom <= count / 2) {
                        return product && isNormal(direct) ? direct : checked(value, 0);
                    }
                    if (level >= FIRST_SAMPLE) {
                        samples.add(value);
                        richardson.add(value);
                    }
                    if (level >= FIRST_ESTIMATE) {
                        double[] estimate = estimate();
                        boolean accepted = shrinks(level) && accuracy(estimate) <= ACCEPTED
                                && agrees(estimate, previousEstimate, AGREE);
                        previousEstimate = estimate;
                        if (accepted) {
                            return checked(estimate[0], estimate[1]);
                        }
                    }
                }
            }
            return atTheEnd();
        }

        private double atTheEnd() {
            if (Math.abs(start) > LARGEST_START) {
                // big start + 2^LEVELS terms is too narrow to tell
                throw new UncalculableException();
            }
            int growth = steadyGrowth();
            if (growth != 0) {
                if (!product) {
                    return growth * Double.POSITIVE_INFINITY;
                }
                if (growth < 0) {
                    return 0;
                }
                return anyNegative() ? Double.NaN : sign * Double.POSITIVE_INFINITY;
            }
            if (termsDoNotShrink()) {
                // still swinging, could be slow growth
                if (drifts()) {
                    throw new UncalculableException();
                }
                return Double.NaN;
            }
            double[] estimate = previousEstimate;
            if (shrinks(LEVELS) && accuracy(estimate) <= LATE_ACCEPTED) {
                return checked(estimate[0], estimate[1]);
            }
            throw new UncalculableException();
        }

        // only use estimate while terms + steps shrink
        private boolean shrinks(int level) {
            for (int k = level - 1; k <= level; k++) {
                if (size[k] >= size[k - 1]) {
                    return false;
                }
                if (Math.abs(step(k)) > SHRINKING_STEPS * Math.abs(step(k - 1))) {
                    return false;
                }
                if (product && negative[k]) {
                    return false;
                }
            }
            return true;
        }

        private double step(int level) {
            return checkpoint[level] - checkpoint[level - 1];
        }

        private double[] estimate() {
            double[] fromRichardson = {richardson.value, richardson.error};
            double[] fromWynn = wynn(samples);
            return fromWynn[1] < fromRichardson[1] ? fromWynn : fromRichardson;
        }

        // only as good as the cancellation lets it be
        private double checked(double value, double error) {
            double roundOff = ROUND_OFF * sumOfSizes;
            if (!isFinite(value) || accuracy(new double[] {value, Math.max(error, roundOff)}) > LATE_ACCEPTED) {
                throw new UncalculableException();
            }
            if (!product) {
                return value;
            }
            double result = sign * Math.exp(value);
            if (!isFinite(result)) {
                throw new UncalculableException();
            }
            return result;
        }

        private double accuracy(double[] estimate) {
            double value = estimate[0];
            double error = estimate[1];
            if (!isFinite(value) || !isFinite(error)) {
                return Double.POSITIVE_INFINITY;
            }
            if (product || error == 0) {
                return error;
            }
            return error / Math.abs(value);
        }

        private boolean agrees(double[] estimate, double[] other, double tolerance) {
            return accuracy(new double[] {estimate[0], Math.abs(estimate[0] - other[0])}) <= tolerance;
        }

        // +1/-1 if partials keep moving one way, else 0
        private int steadyGrowth() {
            for (int direction = -1; direction <= 1; direction += 2) {
                double[] edge = direction > 0 ? low : high;
                if (isSteady(edge, direction)) {
                    return direction;
                }
            }
            return 0;
        }

        private boolean isSteady(double[] edge, int direction) {
            double previousRatio = Double.NaN;
            for (int k = LEVELS - WINDOW + 1; k <= LEVELS; k++) {
                double step = edge[k] - edge[k - 1];
                double previousStep = edge[k - 1] - edge[k - 2];
                if (!isFinite(step) || Math.signum(step) != direction || Math.signum(previousStep) != direction) {
                    return false;
                }
                double ratio = step / previousStep;
                boolean grows = ratio >= 1 - STEADY;
                boolean levelling = Math.abs(1 - ratio) <= LEVELLING * Math.abs(1 - previousRatio);
                if (k > LEVELS - WINDOW + 1 && !grows && !levelling) {
                    return false;
                }
                previousRatio = ratio;
            }
            return previousRatio >= 1 - STEADY || Math.abs(1 - previousRatio) <= LEVELLED;
        }

        // biggest term per block not dropping
        private boolean termsDoNotShrink() {
            double largest = 0;
            for (int k = LEVELS - WINDOW; k <= LEVELS; k++) {
                if (size[k] < (1 - NOT_SHRINKING) * largest) {
                    return false;
                }
                largest = Math.max(largest, size[k]);
            }
            return true;
        }

        // middle of swing drifting more and more
        private boolean drifts() {
            double direction = 0;
            double firstMove = 0;
            double move = 0;
            for (int k = LEVELS - WINDOW + 1; k <= LEVELS; k++) {
                move = middle(k) - middle(k - 1);
                if (move == 0 || (direction != 0 && Math.signum(move) != direction)) {
                    return false;
                }
                if (direction == 0) {
                    firstMove = move;
                }
                direction = Math.signum(move);
            }
            return Math.abs(move) >= Math.abs(firstMove);
        }

        private double middle(int level) {
            return (low[level] + high[level]) / 2;
        }

        private boolean anyNegative() {
            for (int k = LEVELS - WINDOW; k <= LEVELS; k++) {
                if (negative[k]) {
                    return true;
                }
            }
            return false;
        }

        // one undefined term = undefined sum
        private double undefinedTerm(long i) {
            for (long j = 1; j <= LOOK_AHEAD; j++) {
                if (Double.isNaN(term.applyAsDouble(start + i + j))) {
                    throw new UncalculableException();
                }
            }
            return Double.NaN;
        }

        private double infiniteTerm(long i, double a) {
            if (i == 0 && (!product || a > 0)) {
                for (long j = 1; j < ALL_INFINITE; j++) {
                    if (term.applyAsDouble(start + j) != a) {
                        throw new UncalculableException();
                    }
                }
                return a;
            }
            if (product) {
                throw new UncalculableException();
            }
            return overflow(a);
        }

        // overflow counts as inf only if terms grew geometrically
        private double overflow(double a) {
            List<Double> terms = new ArrayList<>();
            for (int j = Math.max(0, recentCount - RECENT); j < recentCount; j++) {
                terms.add(recent[j % RECENT]);
            }
            if (isFinite(a)) {
                terms.add(a);
            }
            if (terms.size() < 3) {
                throw new UncalculableException();
            }
            double direction = Math.signum(a);
            double previousRatio = 1;
            for (int j = 0; j < terms.size(); j++) {
                if (Math.signum(terms.get(j)) != direction) {
                    throw new UncalculableException();
                }
                if (j > 0) {
                    double ratio = terms.get(j) / terms.get(j - 1);
                    if (ratio < previousRatio * (1 - STEADY)) {
                        throw new UncalculableException();
                    }
                    previousRatio = ratio;
                }
            }
            return direction * Double.POSITIVE_INFINITY;
        }

        private void remember(double a) {
            recent[recentCount % RECENT] = a;
            recentCount++;
        }
    }

    // richardson on checkpoints 1,2,4,...
    private static final class Richardson {
        private static final int COLUMNS = 8;
        private final List<double[]> rows = new ArrayList<>();
        private double value = Double.NaN;
        private double error = Double.POSITIVE_INFINITY;

        void add(double sample) {
            int i = rows.size();
            double[] row = new double[Math.min(i, COLUMNS) + 1];
            row[0] = sample;
            value = Double.NaN;
            error = Double.POSITIVE_INFINITY;
            if (i >= 2) {
                consider(sample, Math.max(Math.abs(sample - rows.get(i - 1)[0]), Math.abs(sample - rows.get(i - 2)[0])));
            }
            double factor = 1;
            for (int j = 1; j < row.length; j++) {
                double[] up = rows.get(i - 1);
                factor = factor * 2;
                row[j] = row[j - 1] + (row[j - 1] - up[j - 1]) / (factor - 1);
                double estimate = Math.max(Math.abs(row[j] - row[j - 1]), Math.abs(row[j] - up[j - 1]));
                if (j < up.length) {
                    estimate = Math.max(estimate, Math.abs(row[j] - up[j]));
                }
                consider(row[j], estimate);
            }
            rows.add(row);
        }

        private void consider(double candidate, double candidateError) {
            if (isFinite(candidate) && isFinite(candidateError) && candidateError < error) {
                value = candidate;
                error = candidateError;
            }
        }
    }

    // wynn on checkpoints, best entry w/ newest one
    private static double[] wynn(List<Double> samples) {
        int n = samples.size();
        double[] older = new double[n + 1];
        double[] current = new double[n];
        for (int i = 0; i < n; i++) {
            current[i] = samples.get(i);
        }
        double bestValue = Double.NaN;
        double bestError = Double.POSITIVE_INFINITY;
        for (int k = 1; k <= 8 && current.length > 1; k++) {
            double[] next = new double[current.length - 1];
            for (int m = 0; m < next.length; m++) {
                next[m] = older[m + 1] + 1 / (current[m + 1] - current[m]);
            }
            older = current;
            current = next;
            int last = current.length - 1;
            if (k % 2 == 1 || last < 2) {
                continue;
            }
            double value = current[last];
            double error = Math.max(Math.abs(value - current[last - 1]), Math.abs(value - current[last - 2]));
            if (isFinite(value) && isFinite(error) && error < bestError) {
                bestValue = value;
                bestError = error;
            }
        }
        return new double[] {bestValue, bestError};
    }

    private static boolean isNormal(double value) {
        return isFinite(value) && Math.abs(value) >= Double.MIN_NORMAL;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
