package entity;

import java.util.ArrayList;
import java.util.List;

// is it true somewhere / everywhere, for the quantifiers
final class TruthSearch {
    static final int REAL = 0;
    static final int INTEGER = 1;
    static final int NATURAL = 2;
    static final int FOUND = 0;
    static final int NOT_FOUND = 2;
    private static final int FOUND_AT_ROOT = 1;
    private static final int SKIP = -1;
    private static final int TRUE = SampleTruth.TRUE;
    private static final int FALSE = SampleTruth.FALSE;

    private final Context context;
    private final SideScan scan;
    private final RoundingCheck rounding;
    private final SampleTruth truth;
    private final ConditionRoots roots;
    private final SearchPoints points;
    private final ComparisonFinder comparisons;
    private boolean sawVerdict;
    private boolean skippedRoot;

    TruthSearch(Context context, SideScan scan, RoundingCheck rounding, SampleTruth truth, ConditionRoots roots,
                SearchPoints points, ComparisonFinder comparisons) {
        this.context = context;
        this.scan = scan;
        this.rounding = rounding;
        this.truth = truth;
        this.roots = roots;
        this.points = points;
        this.comparisons = comparisons;
    }

    // true if checked every member and none matched
    boolean noMemberGives(List<Double> points, int wanted) {
        boolean open = false;
        for (double point : points) {
            int result = verdict(point, false);
            if (result == wanted) {
                return false;
            }
            open = open || result == SKIP;
        }
        if (open) {
            throw new UndecidableException();
        }
        return true;
    }

    static int decidedWithoutRoots(int result) {
        if (result == FOUND_AT_ROOT) {
            throw new UndecidableException();
        }
        return result;
    }

    // cheap points, then roots, then far scan to 1e300
    int find(boolean holds, int mode, int range) {
        int wanted = holds ? TRUE : FALSE;
        List<Double> candidates = mode == REAL ? points.cheapPoints(true)
                : points.integerPoints(mode == NATURAL, range);
        for (double point : candidates) {
            if (verdictAt(point) == wanted) {
                return FOUND;
            }
        }
        List<Double> rootValues = roots.sortedRootValues();
        for (ConditionRoots.Root root : roots.all()) {
            int result = checkRoot(root.value, root.pair, wanted, mode,
                    ConditionRoots.probeOffset(root.value, rootValues));
            if (result != NOT_FOUND) {
                return result;
            }
        }
        // might only hold between close roots
        for (double midpoint : mode == REAL ? roots.rootMidpoints() : new ArrayList<Double>()) {
            if (verdictAt(midpoint) == wanted) {
                return FOUND;
            }
        }
        int result = scanFar(wanted, mode);
        // nested quantifier: close call might hold elsewhere
        boolean nestedMiss = roots.isNearMiss() && !context.getVariableValues().isEmpty();
        boolean pastEnd = result == NOT_FOUND && mode == REAL && (scan.closesPastEnd(1) || scan.closesPastEnd(-1));
        if (result == NOT_FOUND && (skippedRoot || !sawVerdict || roots.isUndersampled() || nestedMiss || pastEnd)) {
            throw new UndecidableException();
        }
        return result;
    }

    // real root: 0 sides are expected, dont skip
    private int checkRoot(double root, SidePair pair, int wanted, int mode, double offset) {
        return checkRoot(root, pair, wanted, mode, offset, false);
    }

    // exact meet at domain edge counts
    private int checkRoot(double root, SidePair pair, int wanted, int mode, double offset, boolean edgeRoot) {
        boolean genuine = edgeRoot || scan.isGenuineRoot(pair, root, offset);
        List<Double> samples = new ArrayList<>();
        if (mode == REAL) {
            samples.add(root - offset);
            samples.add(root + offset);
        } else {
            double[] near = {Math.floor(root) - 1, Math.floor(root), Math.ceil(root), Math.ceil(root) + 1,
                    Math.floor(root - offset), Math.ceil(root + offset)};
            for (double n : near) {
                if (mode == INTEGER || n >= 0) {
                    samples.add(n);
                }
            }
        }
        for (double sample : samples) {
            if (rootVerdictAt(sample, genuine) == wanted) {
                return FOUND;
            }
        }
        if (mode == REAL && rootVerdictAt(root, genuine) == wanted) {
            return FOUND_AT_ROOT;
        }
        return NOT_FOUND;
    }

    private int rootVerdictAt(double point, boolean genuine) {
        int reliability = rounding.reliabilityAt(point, genuine);
        if (reliability == RoundingCheck.OUT_OF_RANGE && genuine) {
            skippedRoot = true;
        }
        return reliability == RoundingCheck.RELIABLE ? verdict(point, genuine) : SKIP;
    }

    // scan to 1e300 + dips that might cross 0
    private int scanFar(int wanted, int mode) {
        List<SidePair> pairs = comparisons.numericPairs();
        if (pairs.isEmpty()) {
            return NOT_FOUND;
        }
        boolean dips = context.getVariableValues().isEmpty();
        List<Double> found = new ArrayList<>(roots.sortedRootValues());
        double[][] last = new double[2][pairs.size()];
        double[][] beforeLast = new double[2][pairs.size()];
        double lastSize = 0;
        double beforeLastSize = 0;
        for (int k = 0; k <= SideScan.SCAN_POINTS; k++) {
            double size = EquationSolver.SEARCH_END * Math.pow(10, k / (double) SideScan.POINTS_PER_DECADE);
            for (int side = 0; side < 2; side++) {
                double sign = side == 0 ? 1 : -1;
                for (int i = 0; i < pairs.size(); i++) {
                    double d = scan.scanValue(pairs.get(i), sign * size);
                    double before = last[side][i];
                    double twoBefore = beforeLast[side][i];
                    beforeLast[side][i] = before;
                    last[side][i] = d;
                    if (k > 0 && SideScan.isDomainEdge(before, d)) {
                        // side might just hit the other at domain edge
                        double edge = scan.edgeBetween(pairs.get(i), sign * lastSize, before, sign * size);
                        int result = checkRoot(edge, pairs.get(i), wanted, mode,
                                ConditionRoots.PROBE * Math.max(1, Math.abs(edge)),
                                scan.scanValue(pairs.get(i), edge) == 0);
                        if (result != NOT_FOUND) {
                            return result;
                        }
                    }
                    if (dips && k >= 2 && SideScan.isDip(twoBefore, before, d)) {
                        int result = checkDip(pairs.get(i), sign * beforeLastSize, twoBefore, sign * size,
                                wanted, mode);
                        if (result != NOT_FOUND) {
                            return result;
                        }
                    }
                    if (k == 0 || !Double.isFinite(d) || !Double.isFinite(before) || before == 0 || before * d > 0) {
                        continue;
                    }
                    double root = d == 0 ? sign * size
                            : scan.bisect(pairs.get(i), sign * lastSize, sign * size, before);
                    int result = checkFarRoot(root, pairs.get(i), wanted, mode, found);
                    if (result != NOT_FOUND) {
                        return result;
                    }
                }
            }
            beforeLastSize = lastSize;
            lastSize = size;
        }
        return NOT_FOUND;
    }

    // scan root, keep probes between roots
    private int checkFarRoot(double root, SidePair pair, int wanted, int mode, List<Double> found) {
        double nearest = Double.NaN;
        for (double other : found) {
            if (other != root && (Double.isNaN(nearest) || Math.abs(other - root) < Math.abs(nearest - root))) {
                nearest = other;
            }
        }
        found.add(root);
        double offset = ConditionRoots.PROBE * Math.max(1, Math.abs(root));
        if (!Double.isNaN(nearest)) {
            offset = Math.max(Math.min(offset, Math.abs(root - nearest) / ConditionRoots.NEIGHBOUR_SHARE),
                    ConditionRoots.MIN_PROBE * Math.max(1, Math.abs(root)));
        }
        int result = checkRoot(root, pair, wanted, mode, offset);
        if (result == NOT_FOUND && mode == REAL && !Double.isNaN(nearest)
                && verdictAt((root + nearest) / 2) == wanted) {
            return FOUND;
        }
        return result;
    }

    // lowest pt of dip, past 0 = two roots
    private int checkDip(SidePair pair, double from, double dFrom, double to, int wanted, int mode) {
        double sign = Math.signum(dFrom);
        double[] lowest = EquationSolver.lowest(x -> sign * scan.difference(pair, x), Math.min(from, to),
                Math.max(from, to));
        if (!Double.isFinite(lowest[1]) || lowest[1] > 0) {
            return NOT_FOUND;
        }
        if (verdictAt(lowest[0]) == wanted) {
            return FOUND;
        }
        double first = scan.bisect(pair, from, lowest[0], dFrom);
        double second = scan.bisect(pair, to, lowest[0], scan.difference(pair, to));
        double offset = Math.abs(second - first) / ConditionRoots.NEIGHBOUR_SHARE;
        for (double root : new double[] {first, second}) {
            int result = checkRoot(root, pair, wanted, mode,
                    Math.min(offset, ConditionRoots.PROBE * Math.max(1, Math.abs(root))));
            if (result != NOT_FOUND) {
                return result;
            }
        }
        return NOT_FOUND;
    }

    private int verdictAt(double point) {
        return rounding.reliabilityAt(point, false) == RoundingCheck.RELIABLE ? verdict(point, false) : SKIP;
    }

    // lost value proves nothing unless real root
    private int verdict(double point, boolean atRoot) {
        context.setAbsorbed(false);
        boolean holds;
        try {
            holds = truth.isTrueAt(point);
        } catch (OverflowException e) {
            return SKIP;
        }
        if (context.isAbsorbed() && !atRoot) {
            return SKIP;
        }
        sawVerdict = true;
        return holds ? TRUE : FALSE;
    }
}
