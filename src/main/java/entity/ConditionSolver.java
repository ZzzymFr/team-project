package entity;

import entity.calculus.Rounding;
import entity.node.Node;
import entity.node.QuantifierNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConditionSolver {
    private static final double GRID_STEP = 0.01;
    private static final double NESTED_STEP = 1;
    private static final double SNAP = 1e-6;
    private static final double NEARLY_EQUAL = 1e-8;
    // bisection can land just past the 1e-9 tol
    private static final double ZERO_SNAP = 2e-9;
    private static final int MAX_ISOLATED_POINTS = 10;
    private static final int MAX_ROUNDS = 1100;
    private static final double BISECT_GAP = 1e-15;
    private static final int EDGE_DIGITS = 12;
    private static final double INCLUDED = 1;

    private final Node condition;
    private final String variable;
    private final Context context;
    private final ConditionRoots conditionRoots;
    private final SearchPoints searchPoints;
    private final SideScan scan;
    private final SampleTruth sampleTruth;
    private final TruthSearch search;
    private final FarBoundaries far;

    public ConditionSolver(Node condition, String variable, Context context) {
        this.condition = condition;
        this.variable = variable;
        this.context = context;
        boolean nested = !context.getVariableValues().isEmpty() || containsQuantifier(condition);
        double step = nested ? NESTED_STEP : GRID_STEP;
        ComparisonFinder comparisons = new ComparisonFinder(condition, variable, context);
        RoundingCheck rounding = new RoundingCheck(variable, context, comparisons);
        this.conditionRoots = new ConditionRoots(variable, context, step, comparisons);
        this.searchPoints = new SearchPoints(context, step, conditionRoots);
        this.scan = new SideScan(variable, context, comparisons);
        this.sampleTruth = new SampleTruth(condition, variable, context, rounding);
        this.search = new TruthSearch(context, scan, rounding, sampleTruth, conditionRoots, searchPoints,
                comparisons);
        this.far = new FarBoundaries(scan, comparisons, sampleTruth);
    }

    private static boolean containsQuantifier(Node node) {
        if (node instanceof QuantifierNode) {
            return true;
        }
        for (Node child : node.children()) {
            if (containsQuantifier(child)) {
                return true;
            }
        }
        return false;
    }

    List<Double> boundaryRoots() {
        return conditionRoots.values();
    }

    // intervals where it holds, [-100,100] + the few far roots the scan finds (R31)
    public List<Interval> solve() {
        List<Interval> intervals = solveInRange();
        boolean below = intervals.isEmpty() || intervals.get(0).getLow() == Double.NEGATIVE_INFINITY;
        boolean above = intervals.isEmpty()
                || intervals.get(intervals.size() - 1).getHigh() == Double.POSITIVE_INFINITY;
        boolean edgeTruth = !intervals.isEmpty();
        if (below && scan.closesPastEnd(-1) || above && scan.closesPastEnd(1)) {
            throw new UncalculableException(UncalculableReason.BEYOND_RANGE);
        }
        if (below && flipsBeyond(-1, edgeTruth) || above && flipsBeyond(1, edgeTruth)) {
            // keeps flipping out there = endless solutions, same if it was already flipping all the way in range
            List<Double> farRoots = intervals.size() > MAX_ISOLATED_POINTS ? null : rootsBeyond(MAX_ISOLATED_POINTS);
            if (farRoots == null) {
                throw new UncalculableException(UncalculableReason.TOO_MANY_SOLUTIONS);
            }
            return solveWithFarRoots(farRoots);
        }
        return withFarRootsIfSure(intervals, !below, !above);
    }

    // false at the range end (1/x<0.001): far part only added when its clear, else range answer as is
    private List<Interval> withFarRootsIfSure(List<Interval> inRange, boolean checkBelow, boolean checkAbove) {
        try {
            if (inRange.size() > MAX_ISOLATED_POINTS
                    || !(checkBelow && flipsBeyond(-1, false) || checkAbove && flipsBeyond(1, false))) {
                return inRange;
            }
            List<Double> farRoots = rootsBeyond(MAX_ISOLATED_POINTS);
            return farRoots == null ? inRange : solveWithFarRoots(farRoots);
        } catch (UncalculableException e) {
            return inRange;
        }
    }

    // far roots as candidates too, only kept if the far scan agrees
    private List<Interval> solveWithFarRoots(List<Double> farRoots) {
        try {
            List<Interval> intervals = solveWith(FarBoundaries.pastRange(farRoots));
            if (far.agrees(intervals)) {
                return intervals;
            }
        } catch (UndecidableException e) {
            throw e;
        } catch (UncalculableException e) {
            // cant tell out there
        }
        throw new UncalculableException(UncalculableReason.BEYOND_RANGE);
    }

    // sides meeting past range only counts if truth flips (sin x<=1 just touches)
    private boolean flipsBeyond(double sign, boolean edgeTruth) {
        List<Double> roots = new ArrayList<>();
        if (!scan.changesBeyond(sign, roots, MAX_ISOLATED_POINTS)) {
            return false;
        }
        if (roots.isEmpty()) {
            return true;
        }
        try {
            for (double root : roots) {
                double offset = SideScan.ROOT_OFFSET * Math.max(1, Math.abs(root));
                if (sampleTruth.isTrueAt(root) != edgeTruth || sampleTruth.isTrueAt(root - offset) != edgeTruth
                        || sampleTruth.isTrueAt(root + offset) != edgeTruth) {
                    return true;
                }
            }
            for (int k = 0; k <= SideScan.SCAN_POINTS; k++) {
                double point = sign * EquationSolver.SEARCH_END * Math.pow(10, k / (double) SideScan.POINTS_PER_DECADE);
                if (sampleTruth.isTrueAt(point) != edgeTruth) {
                    return true;
                }
            }
        } catch (BudgetExceededException | UndecidableException e) {
            throw e;
        } catch (UncalculableException e) {
            return true;
        }
        return false;
    }

    // intervals in range, ends as unbounded
    public List<Interval> solveInRange() {
        return solveWith(new ArrayList<Double>());
    }

    private List<Interval> solveWith(List<Double> farRoots) {
        double[] points = searchPoints.sortedCandidates(farRoots);
        if (conditionRoots.isUndersampled()) {
            throw new UncalculableException(UncalculableReason.TOO_FAST);
        }
        Set<Double> genuineRoots = new HashSet<>();
        List<Double> sortedRoots = conditionRoots.sortedRootValues();
        for (ConditionRoots.Root root : conditionRoots.all()) {
            double offset = ConditionRoots.probeOffset(root.value, sortedRoots);
            if (scan.isGenuineRoot(root.pair, root.value, offset)) {
                genuineRoots.add(root.value);
            }
        }
        genuineRoots.addAll(far.genuine(farRoots));
        boolean[] truth = sampleTruth.truthAt(points, genuineRoots);
        Set<Double> rootValues = new HashSet<>(conditionRoots.values());
        rootValues.addAll(farRoots);
        Set<Double> midpoints = new HashSet<>(conditionRoots.rootMidpoints());
        midpoints.addAll(searchPoints.farGaps(farRoots));

        List<Interval> intervals = new ArrayList<>();
        int isolatedPoints = 0;
        int i = 0;
        while (i < points.length) {
            if (!truth[i]) {
                i++;
                continue;
            }
            int start = i;
            while (i + 1 < points.length && truth[i + 1]) {
                i++;
            }
            int end = i;
            // lone true sample only shown if false right next to it
            if (start == end && !rootValues.contains(points[start]) && !midpoints.contains(points[start])
                    && !runsPastEnd(points, start)) {
                isolatedPoints++;
                if (isolatedPoints > MAX_ISOLATED_POINTS || !isIsolated(points[start], true)) {
                    throw new UncalculableException(UncalculableReason.TOO_MANY_SOLUTIONS);
                }
                addInterval(intervals, new Interval(points[start], true, points[start], true));
                i++;
                continue;
            }

            double low = Double.NEGATIVE_INFINITY;
            boolean lowIncluded = false;
            if (start > 0 && isLoneExclusion(points, truth, start - 1)) {
                low = points[start - 1];
            } else if (start > 0) {
                double[] edge = boundary(points[start - 1], points[start], farRoots);
                low = edge[0];
                lowIncluded = edge[1] == INCLUDED;
            }
            double high = Double.POSITIVE_INFINITY;
            boolean highIncluded = false;
            if (end < points.length - 1 && isLoneExclusion(points, truth, end + 1)) {
                high = points[end + 1];
            } else if (end < points.length - 1) {
                double[] edge = boundary(points[end + 1], points[end], farRoots);
                high = edge[0];
                highIncluded = edge[1] == INCLUDED;
            }
            addInterval(intervals, new Interval(low, lowIncluded, high, highIncluded));
            i++;
        }
        return intervals;
    }

    // true sample on the very end that stays true further out is a ray (x > 100), not a point
    private boolean runsPastEnd(double[] points, int index) {
        double offset = SideScan.ROOT_OFFSET * Math.max(1, Math.abs(points[index]));
        if (index == points.length - 1) {
            return sampleTruth.isTrueAt(points[index] + offset);
        }
        return index == 0 && sampleTruth.isTrueAt(points[index] - offset);
    }

    // single false between trues is the boundary itself
    private boolean isLoneExclusion(double[] points, boolean[] truth, int index) {
        return index > 0 && index < points.length - 1 && truth[index - 1] && truth[index + 1]
                && isIsolated(points[index], false);
    }

    // true if differs right below and above
    private boolean isIsolated(double point, boolean truth) {
        double offset = SideScan.ROOT_OFFSET * Math.max(1, Math.abs(point));
        return sampleTruth.isTrueAt(point - offset) != truth && sampleTruth.isTrueAt(point + offset) != truth;
    }

    public boolean isTrueSomewhere() {
        return search.find(true, TruthSearch.REAL, 0) != TruthSearch.NOT_FOUND;
    }

    public boolean isTrueEverywhere() {
        return search.find(false, TruthSearch.REAL, 0) == TruthSearch.NOT_FOUND;
    }

    // over Q a root may not be a member, undecidable
    public boolean isTrueSomewhereRational() {
        return TruthSearch.decidedWithoutRoots(search.find(true, TruthSearch.REAL, 0)) == TruthSearch.FOUND;
    }

    public boolean isTrueEverywhereRational() {
        return TruthSearch.decidedWithoutRoots(search.find(false, TruthSearch.REAL, 0)) == TruthSearch.NOT_FOUND;
    }

    public boolean isTrueForSomeInteger(boolean naturalOnly, int range) {
        int mode = naturalOnly ? TruthSearch.NATURAL : TruthSearch.INTEGER;
        return search.find(true, mode, range) != TruthSearch.NOT_FOUND;
    }

    public boolean isTrueForEveryInteger(boolean naturalOnly, int range) {
        int mode = naturalOnly ? TruthSearch.NATURAL : TruthSearch.INTEGER;
        return search.find(false, mode, range) == TruthSearch.NOT_FOUND;
    }

    public boolean isTrueForSome(List<Double> points) {
        return !search.noMemberGives(points, SampleTruth.TRUE);
    }

    public boolean isTrueForEvery(List<Double> points) {
        return search.noMemberGives(points, SampleTruth.FALSE);
    }

    // roots past range out to 1e300, null if too many
    public List<Double> rootsBeyond(int limit) {
        List<Double> roots = new ArrayList<>();
        scan.changesBeyond(-1, roots, limit);
        scan.changesBeyond(1, roots, limit);
        if (roots.size() > limit) {
            return null;
        }
        if (scan.closesPastEnd(-1) || scan.closesPastEnd(1)) {
            throw new UncalculableException(UncalculableReason.BEYOND_RANGE);
        }
        Collections.sort(roots);
        return roots;
    }

    // {boundary, INCLUDED?} between false and true
    private double[] boundary(double falsePoint, double truePoint, List<Double> farRoots) {
        // far gaps can span decades, keep going till the gap is tiny for its size
        for (int round = 0; round < SideScan.ROUNDS || round < MAX_ROUNDS
                && Math.abs(truePoint - falsePoint) > BISECT_GAP * Math.max(1, Math.abs(truePoint)); round++) {
            double middle = (falsePoint + truePoint) / 2;
            if (sampleTruth.isTrueAt(middle)) {
                truePoint = middle;
            } else {
                falsePoint = middle;
            }
        }
        double limit = (falsePoint + truePoint) / 2;
        // domain edge is its own boundary
        boolean domainEdge = isUndefinedAt(falsePoint);
        double snapped = FarBoundaries.snap(limit, farRoots);
        if (Double.isNaN(snapped)) {
            snapped = tidy(limit, !domainEdge);
        }
        if (!Double.isNaN(snapped)) {
            return new double[] {snapped, sampleTruth.isTrueAt(snapped) ? INCLUDED : 0};
        }
        if (domainEdge && sampleTruth.isTrueAt(truePoint)) {
            // pole next door isnt an included end (1/(x+2) at -2)
            double tidyEdge = Rounding.toSignificant(truePoint, EDGE_DIGITS);
            if (tidyEdge != truePoint && isUndefinedAt(tidyEdge)) {
                return new double[] {tidyEdge, 0};
            }
            if (tidyEdge != truePoint && sampleTruth.isTrueAt(tidyEdge)) {
                return new double[] {tidyEdge, INCLUDED};
            }
            return new double[] {truePoint, INCLUDED};
        }
        return new double[] {limit, 0};
    }

    private boolean isUndefinedAt(double point) {
        context.spendStep();
        try {
            condition.evaluate(context.withVariable(variable, point));
            return false;
        } catch (UndefinedException e) {
            return true;
        }
    }

    // root for bisected boundary, else NaN
    private double tidy(double point, boolean nearlyEqual) {
        ConditionRoots.Root nearest = null;
        for (ConditionRoots.Root root : conditionRoots.all()) {
            if (nearest == null || Math.abs(root.value - point) < Math.abs(nearest.value - point)) {
                nearest = root;
            }
        }
        if (nearest != null) {
            double distance = Math.abs(nearest.value - point);
            if (distance < SNAP || nearlyEqual && !nearest.stretchEnd && isNearlyEqualAt(nearest, point)) {
                return nearest.value;
            }
        }
        if (Math.abs(point) <= ZERO_SNAP) {
            return 0;
        }
        return Double.NaN;
    }

    // inside tol of a root the root is the real boundary
    private boolean isNearlyEqualAt(ConditionRoots.Root root, double point) {
        Context withPoint = context.withVariable(variable, point);
        try {
            double a = root.pair.leftAt(withPoint);
            double b = root.pair.rightAt(withPoint);
            if (!Double.isFinite(a) || !Double.isFinite(b)) {
                return false;
            }
            double size = Math.max(1, Math.max(Math.abs(a), Math.abs(b)));
            return Math.abs(a - b) <= NEARLY_EQUAL * size;
        } catch (BudgetExceededException | UndecidableException e) {
            throw e;
        } catch (UncalculableException e) {
            return false;
        }
    }

    private void addInterval(List<Interval> intervals, Interval interval) {
        if (!intervals.isEmpty()) {
            Interval last = intervals.get(intervals.size() - 1);
            boolean touching = last.getHigh() == interval.getLow()
                    && (last.isHighIncluded() || interval.isLowIncluded());
            if (last.getHigh() > interval.getLow() || touching) {
                intervals.set(intervals.size() - 1, new Interval(last.getLow(), last.isLowIncluded(),
                        interval.getHigh(), interval.isHighIncluded()));
                return;
            }
        }
        intervals.add(interval);
    }
}
