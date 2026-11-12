package entity;

import entity.value.Values;

import java.util.List;

// left-right of one comparison at a point + the scan past [-100,100]
final class SideScan {
    static final double ROOT_OFFSET = 1e-7;
    static final int ROUNDS = 60;
    static final int POINTS_PER_DECADE = 3;
    static final int SCAN_POINTS = 3 * 298;
    static final double TINY = 1e-300;
    private static final double TOUCHING = 1e-6;
    private static final int TAIL_POINTS = 4;
    private static final double CLOSING_GAP = 1e-6;
    private static final double SLOW_SHRINK = 0.99;

    private final String variable;
    private final Context context;
    private final ComparisonFinder comparisons;

    SideScan(String variable, Context context, ComparisonFinder comparisons) {
        this.variable = variable;
        this.context = context;
        this.comparisons = comparisons;
    }

    // left - right for far scans, NaN/inf when unknown
    double scanValue(SidePair pair, double point) {
        context.spendStep();
        context.setAbsorbed(false);
        double d;
        try {
            d = pair.differenceAt(context.withVariable(variable, point));
        } catch (UndefinedException e) {
            return Double.NaN;
        } catch (BudgetExceededException | UndecidableException e) {
            throw e;
        } catch (UncalculableException e) {
            return Double.POSITIVE_INFINITY;
        }
        // lost value can give NaN too (inf - inf)
        if (Double.isInfinite(d) || context.isAbsorbed() || pair.isNoise(context)) {
            return Double.POSITIVE_INFINITY;
        }
        return d;
    }

    double difference(SidePair pair, double point) {
        context.spendStep();
        try {
            return pair.differenceAt(context.withVariable(variable, point));
        } catch (BudgetExceededException | UndecidableException e) {
            throw e;
        } catch (UncalculableException e) {
            return Double.NaN;
        }
    }

    double bisect(SidePair pair, double low, double high, double dLow) {
        for (int round = 0; round < ROUNDS; round++) {
            double middle = (low + high) / 2;
            double dMiddle = difference(pair, middle);
            if (dMiddle == 0 || !Double.isFinite(dMiddle)) {
                return middle;
            }
            if (dLow * dMiddle < 0) {
                high = middle;
            } else {
                low = middle;
                dLow = dMiddle;
            }
        }
        return (low + high) / 2;
    }

    // root to the last bit, NaN if sign doesnt flip within maxWidth
    double pinRoot(SidePair pair, double root, double maxWidth) {
        double dRoot = difference(pair, root);
        if (dRoot == 0 || !Double.isFinite(dRoot)) {
            return Double.NaN;
        }
        for (double width = Math.ulp(root); width <= maxWidth; width *= 2) {
            double dBelow = difference(pair, root - width);
            if (dBelow * dRoot < 0) {
                return bisect(pair, root - width, root, dBelow);
            }
            if (difference(pair, root + width) * dRoot < 0) {
                return bisect(pair, root, root + width, dRoot);
            }
        }
        return Double.NaN;
    }

    // defined end of the edge
    double edgeBetween(SidePair pair, double from, double dFrom, double to) {
        double defined = Double.isNaN(dFrom) ? to : from;
        double undefined = Double.isNaN(dFrom) ? from : to;
        for (int round = 0; round < ROUNDS; round++) {
            double middle = (defined + undefined) / 2;
            if (Double.isNaN(scanValue(pair, middle))) {
                undefined = middle;
            } else {
                defined = middle;
            }
        }
        return defined;
    }

    static boolean isDomainEdge(double before, double now) {
        return Double.isNaN(before) && Double.isFinite(now) || Double.isFinite(before) && Double.isNaN(now);
    }

    // 3 same sign vals, middle closest to 0
    static boolean isDip(double first, double middle, double last) {
        return Double.isFinite(first) && Double.isFinite(middle) && Double.isFinite(last)
                && first * middle > 0 && middle * last > 0
                && Math.abs(middle) < Math.abs(first) && Math.abs(middle) < Math.abs(last);
    }

    // sides cross/meet w/ gaps, nothing lost to rounding
    boolean isGenuineRoot(SidePair pair, double root, double offset) {
        context.setAbsorbed(false);
        double here = difference(pair, root);
        double below = difference(pair, root - offset);
        double above = difference(pair, root + offset);
        if (context.isAbsorbed() || !Double.isFinite(below) || !Double.isFinite(above)) {
            return false;
        }
        boolean touching = here == 0 || Math.abs(here) <= TOUCHING * Math.min(Math.abs(below), Math.abs(above));
        return below * above < 0 || (touching && Math.abs(below) >= TINY && Math.abs(above) >= TINY);
    }

    // sides still closing in at 1e300 = root past what doubles hold (ln x=1000)
    boolean closesPastEnd(double sign) {
        for (SidePair pair : comparisons.numericPairs()) {
            if (closesPastEnd(pair, sign)) {
                return true;
            }
        }
        return false;
    }

    private boolean closesPastEnd(SidePair pair, double sign) {
        double[] gap = new double[TAIL_POINTS];
        double direction = 0;
        boolean logGap = true;
        double scale = 1;
        for (int j = 0; j < TAIL_POINTS; j++) {
            int k = SCAN_POINTS - TAIL_POINTS + 1 + j;
            scanValue(pair, sign * EquationSolver.SEARCH_END * Math.pow(10, k / (double) POINTS_PER_DECADE));
            double a = pair.lastLeft;
            double b = pair.lastRight;
            if (!Double.isFinite(a) || !Double.isFinite(b) || a == b) {
                return false;
            }
            // ratio not difference, so 1e150 vs 1e200 isnt absorbed
            logGap = logGap && a * b > 0;
            double signed = a * b > 0 ? Math.log(Math.abs(a)) - Math.log(Math.abs(b)) : a - b;
            if (direction == 0) {
                direction = Math.signum(signed);
            } else if (Math.signum(signed) != direction) {
                return false;
            }
            gap[j] = Math.abs(signed);
            scale = Math.max(scale, Math.max(Math.abs(a), Math.abs(b)));
        }
        int last = TAIL_POINTS - 1;
        // gap ~0 = just an asymptote (1/x=0)
        if (gap[last] <= CLOSING_GAP * (logGap ? 1 : scale)) {
            return false;
        }
        for (int j = 1; j < TAIL_POINTS; j++) {
            if (gap[j] >= gap[j - 1]) {
                return false;
            }
        }
        double earlierStep = gap[last - 2] - gap[last - 1];
        double lastStep = gap[last - 1] - gap[last];
        double ratio = lastStep / earlierStep;
        // barely slowing (ln ln x) could still get there
        if (ratio >= SLOW_SHRINK) {
            return true;
        }
        // shrinking steps: does what is left still reach 0
        return lastStep * ratio / (1 - ratio) >= gap[last];
    }

    // scan past the search end for sign changes
    boolean changesBeyond(double sign, List<Double> roots, int limit) {
        boolean changes = false;
        for (SidePair pair : comparisons.numericPairs()) {
            double known = Double.POSITIVE_INFINITY;
            double knownPoint = 0;
            double before = Double.POSITIVE_INFINITY;
            double twoBefore = Double.POSITIVE_INFINITY;
            double lastPoint = 0;
            double beforeLastPoint = 0;
            for (int k = 0; k <= SCAN_POINTS && roots.size() <= limit; k++) {
                double point = sign * EquationSolver.SEARCH_END * Math.pow(10, k / (double) POINTS_PER_DECADE);
                double d = scanValue(pair, point);
                // within tol only rounding shows. a root right on the range end isnt beyond it either
                boolean onRangeEnd = k == 0 && d == 0;
                if ((d != 0 && Double.isFinite(d) && Values.areAlmostEqual(pair.lastLeft, pair.lastRight))
                        || onRangeEnd) {
                    d = Double.POSITIVE_INFINITY;
                }
                if (Double.isInfinite(d)) {
                    twoBefore = before;
                    before = d;
                    continue;
                }
                if (k > 0 && !Double.isInfinite(known)) {
                    if (Double.isFinite(d) && Double.isFinite(known) && Math.signum(d) != Math.signum(known)) {
                        changes = true;
                        if (d == 0) {
                            addRoot(roots, point);
                        } else if (known != 0) {
                            addCheckedRoot(roots, pair, bisect(pair, knownPoint, point, known));
                        }
                    } else if (isDomainEdge(known, d)) {
                        changes = true;
                        addCheckedRoot(roots, pair, edgeBetween(pair, knownPoint, known, point));
                    }
                }
                known = d;
                knownPoint = point;
                if (k >= 2 && isDip(twoBefore, before, d)) {
                    double from = beforeLastPoint;
                    double dipSign = Math.signum(twoBefore);
                    double[] lowest = EquationSolver.lowest(x -> dipSign * difference(pair, x),
                            Math.min(from, point), Math.max(from, point));
                    if (Double.isFinite(lowest[1]) && lowest[1] <= 0) {
                        changes = true;
                        addCheckedRoot(roots, pair, bisect(pair, from, lowest[0], twoBefore));
                        addCheckedRoot(roots, pair, bisect(pair, point, lowest[0], d));
                    }
                }
                twoBefore = before;
                before = d;
                beforeLastPoint = lastPoint;
                lastPoint = point;
            }
        }
        return changes;
    }

    // scan root, only where sides really meet
    private void addCheckedRoot(List<Double> roots, SidePair found, double root) {
        context.spendStep();
        SidePair pair = found.whole == null ? found : found.whole;
        try {
            Context at = context.withVariable(variable, root);
            double a = pair.leftAt(at);
            double b = pair.rightAt(at);
            if (Double.isFinite(a) && Double.isFinite(b) && Values.areAlmostEqual(a, b)) {
                addRoot(roots, root);
            }
        } catch (BudgetExceededException | UndecidableException e) {
            throw e;
        } catch (UncalculableException e) {
            // no root
        }
    }

    private static void addRoot(List<Double> roots, double root) {
        for (double other : roots) {
            if (Math.abs(other - root) <= ROOT_OFFSET * Math.max(1, Math.abs(root))) {
                return;
            }
        }
        roots.add(root);
    }
}
