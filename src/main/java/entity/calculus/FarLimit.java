package entity.calculus;

import entity.UncalculableException;
import entity.UncalculableReason;

import java.util.ArrayList;
import java.util.List;

// keeps going way past the normal run (x out to 1e300) and reads the answer off the far end
final class FarLimit {
    private static final double SHRINK = 1e10;
    private static final double CLEAN = 1e-7;
    private static final double SETTLED = 1e-9;
    private static final int SETTLED_RUN = 3;
    private static final int GROWTH_STEPS = 3;
    private static final double STEADY_GROWTH = 0.999;

    private FarLimit() {
    }

    static boolean canGoFrom(LimitSide side, double h) {
        return side.reaches(h / SHRINK);
    }

    // nearest = last sample of the normal run, NaN = nothing clean out there (cot x, e^x/e^x)
    static double limit(LimitSide side, double h, double nearest) {
        int outermost = 0;
        while (side.reaches(h / Math.pow(SHRINK, outermost + 1))) {
            outermost++;
        }
        // clean out to some edge, overflow/NaN past it, find the edge by halving
        int edge = outermost;
        double stop = cleanAt(side, h, outermost);
        if (Double.isFinite(stop)) {
            stop = Double.NaN;
        } else if (!Double.isFinite(cleanAt(side, h, 1))) {
            return Double.NaN;
        } else {
            int clean = 1;
            while (edge - clean > 1) {
                int mid = (clean + edge) / 2;
                double y = cleanAt(side, h, mid);
                if (Double.isFinite(y)) {
                    clean = mid;
                } else {
                    edge = mid;
                    stop = y;
                }
            }
            edge = clean;
        }
        List<Double> values = new ArrayList<>();
        int k = edge;
        for (; k >= 1 && values.size() <= GROWTH_STEPS; k--) {
            double y = cleanAt(side, h, k);
            if (!Double.isFinite(y)) {
                break;
            }
            values.add(0, y);
        }
        if (k == 0) {
            values.add(0, nearest);
        }
        if (Double.isInfinite(stop) && headsTo(values, stop)) {
            return stop;
        }
        double growth = growth(values);
        if (growth != 0) {
            return growth * Double.POSITIVE_INFINITY;
        }
        return settled(values);
    }

    // sample at grid point k, NaN if it isnt clean
    private static double cleanAt(LimitSide side, double h, int k) {
        double at = h / Math.pow(SHRINK, k);
        double y = side.valueAt(at);
        if (Double.isFinite(y) && !isClean(side, at, y)) {
            return Double.NaN;
        }
        return y;
    }

    // lost something to rounding and no idea how much it matters (sqrt(x^2+x)-x) -> skip
    private static boolean isClean(LimitSide side, double at, double y) {
        boolean absorbed = side.absorbedAt(at);
        double noise = side.noiseAt(at);
        if (!Double.isFinite(noise)) {
            return !absorbed;
        }
        return noise <= CLEAN * Math.max(1, Math.abs(y));
    }

    private static boolean headsTo(List<Double> values, double infinity) {
        int n = values.size();
        return n >= 2 && Math.signum(values.get(n - 1) - values.get(n - 2)) == Math.signum(infinity);
    }

    // +1/-1 if the last steps keep the same way and dont shrink
    private static double growth(List<Double> values) {
        int n = values.size();
        if (n <= GROWTH_STEPS) {
            return 0;
        }
        double direction = Math.signum(values.get(n - 1) - values.get(n - 2));
        for (int i = n - GROWTH_STEPS; i < n; i++) {
            double step = values.get(i) - values.get(i - 1);
            if (step == 0 || Math.signum(step) != direction) {
                return 0;
            }
            if (i > n - GROWTH_STEPS && Math.abs(step) < STEADY_GROWTH * Math.abs(values.get(i - 1) - values.get(i - 2))) {
                return 0;
            }
        }
        return direction;
    }

    private static double settled(List<Double> values) {
        int n = values.size();
        double last = values.get(n - 1);
        double spread = 0;
        int run = 0;
        for (int i = n - 1; i >= 0; i--) {
            double gap = Math.abs(values.get(i) - last);
            if (gap > SETTLED * Math.max(1, Math.abs(last))) {
                break;
            }
            spread = Math.max(spread, gap);
            run++;
        }
        if (run < SETTLED_RUN) {
            throw new UncalculableException(UncalculableReason.LIMIT);
        }
        return LimitCalculator.snapped(last, spread, last);
    }
}
