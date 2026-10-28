package entity;

import entity.node.BinaryNode;
import entity.node.FunctionNode;
import entity.node.LiteralNode;
import entity.node.Node;
import entity.node.QuantifierNode;
import entity.node.RelationNode;
import entity.node.UnaryNode;
import entity.value.BooleanValue;
import entity.value.FiniteSetValue;
import entity.value.NumberValue;
import entity.value.Value;
import entity.value.Values;
import entity.value.VectorValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ConditionSolver {
    private static final double GRID_STEP = 0.01;
    private static final double NESTED_STEP = 1;
    private static final double ROOT_OFFSET = 1e-7;
    private static final double SNAP = 1e-6;
    private static final double NEARLY_EQUAL = 1e-8;
    // bisection can land just past the 1e-9 tol
    private static final double ZERO_SNAP = 2e-9;
    private static final int ROUNDS = 60;
    private static final double[] SPECIAL_POINTS = {0, 0.5, -0.5, 1, -1};
    private static final double[] FAR_POINTS = {1e3, -1e3, 1e6, -1e6};
    private static final double OUTER_SCALE = 10;
    private static final double[] FAR_INTEGERS = {1e3, 1e6, 1e9, 1e12};
    private static final double PROBE = 1e-4;
    private static final double MIN_PROBE = 1e-12;
    private static final double NEIGHBOUR_SHARE = 4;
    private static final int POINTS_PER_DECADE = 3;
    private static final int SCAN_POINTS = 3 * 298;
    private static final double TINY = 1e-300;
    private static final double HUGE = 1e300;
    private static final double NOISE = 1e-12;
    private static final double TOUCHING = 1e-6;
    private static final int MAX_ISOLATED_POINTS = 10;
    private static final int REAL = 0;
    private static final int INTEGER = 1;
    private static final int NATURAL = 2;
    private static final int TRUE = 1;
    private static final int FALSE = 0;
    private static final int SKIP = -1;
    private static final int UNKNOWN = 2;
    private static final int RELIABLE = 0;
    private static final int NOISY = 1;
    private static final int OUT_OF_RANGE = 2;
    private static final int FOUND = 0;
    private static final int FOUND_AT_ROOT = 1;
    private static final int NOT_FOUND = 2;
    private static final double INCLUDED = 1;
    private static final int NUMBER_SHAPE = 0;
    private static final int VECTOR_SHAPE = 1;
    private static final int SET_SHAPE = 2;
    private static final int OTHER_SHAPE = 3;
    private static final int MAX_ELEMENT_PAIRS = 16;
    private static final double[] SHAPE_POINTS = {0.5, -7.3, 2.9, 13.7};

    private final Node condition;
    private final String variable;
    private final Context context;
    private final double step;
    private List<Root> roots;
    private List<RelationNode> relations;
    private boolean sawVerdict;
    private boolean skippedRoot;
    private boolean lostInSample;
    private boolean undersampled;
    private boolean nearMiss;

    public ConditionSolver(Node condition, String variable, Context context) {
        this.condition = condition;
        this.variable = variable;
        this.context = context;
        boolean nested = !context.getVariableValues().isEmpty() || containsQuantifier(condition);
        this.step = nested ? NESTED_STEP : GRID_STEP;
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

    // intervals where it holds, only [-100,100] searched (R31)
    public List<Interval> solve() {
        List<Interval> intervals = solveInRange();
        boolean below = intervals.isEmpty() || intervals.get(0).getLow() == Double.NEGATIVE_INFINITY;
        boolean above = intervals.isEmpty()
                || intervals.get(intervals.size() - 1).getHigh() == Double.POSITIVE_INFINITY;
        if (below && changesBeyond(-1, new ArrayList<Double>(), 0)
                || above && changesBeyond(1, new ArrayList<Double>(), 0)) {
            // keeps flipping out there = endless solutions
            throw new UncalculableException(rootsBeyond(MAX_ISOLATED_POINTS) == null
                    ? UncalculableException.TOO_MANY_SOLUTIONS : UncalculableException.BEYOND_RANGE);
        }
        return intervals;
    }

    // intervals in range, ends as unbounded
    public List<Interval> solveInRange() {
        double[] points = sortedCandidates();
        if (undersampled) {
            throw new UncalculableException(UncalculableException.TOO_FAST);
        }
        Set<Double> genuineRoots = new HashSet<>();
        List<Double> sortedRoots = sortedRootValues();
        for (Root root : getRoots()) {
            double offset = probeOffset(root.value, sortedRoots);
            if (isGenuineRoot(root.pair, root.value, offset)) {
                genuineRoots.add(root.value);
            }
        }
        Sample[] samples = new Sample[points.length];
        for (int i = 0; i < points.length; i++) {
            samples[i] = sampleAt(points[i], genuineRoots.contains(points[i]));
        }
        boolean[] truth = withRoundingRunsFilled(samples);
        Set<Double> rootValues = new HashSet<>(boundaryRoots());
        Set<Double> midpoints = new HashSet<>(rootMidpoints());

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
            if (start == end && !rootValues.contains(points[start]) && !midpoints.contains(points[start])) {
                isolatedPoints++;
                if (isolatedPoints > MAX_ISOLATED_POINTS || !isIsolated(points[start], true)) {
                    throw new UncalculableException(UncalculableException.TOO_MANY_SOLUTIONS);
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
                double[] edge = boundary(points[start - 1], points[start]);
                low = edge[0];
                lowIncluded = edge[1] == INCLUDED;
            }
            double high = Double.POSITIVE_INFINITY;
            boolean highIncluded = false;
            if (end < points.length - 1 && isLoneExclusion(points, truth, end + 1)) {
                high = points[end + 1];
            } else if (end < points.length - 1) {
                double[] edge = boundary(points[end + 1], points[end]);
                high = edge[0];
                highIncluded = edge[1] == INCLUDED;
            }
            addInterval(intervals, new Interval(low, lowIncluded, high, highIncluded));
            i++;
        }
        return intervals;
    }

    // single false between trues is the boundary itself
    private boolean isLoneExclusion(double[] points, boolean[] truth, int index) {
        return index > 0 && index < points.length - 1 && truth[index - 1] && truth[index + 1]
                && isIsolated(points[index], false);
    }

    // true if differs right below and above
    private boolean isIsolated(double point, boolean truth) {
        double offset = ROOT_OFFSET * Math.max(1, Math.abs(point));
        return isTrueAt(point - offset) != truth && isTrueAt(point + offset) != truth;
    }

    // rounding runs take truth from neighbours
    private static boolean[] withRoundingRunsFilled(Sample[] samples) {
        boolean[] truth = new boolean[samples.length];
        int i = 0;
        while (i < samples.length) {
            if (samples[i].known != UNKNOWN) {
                truth[i] = samples[i].known == TRUE;
                i++;
                continue;
            }
            int start = i;
            boolean lost = false;
            while (i < samples.length && samples[i].known == UNKNOWN) {
                truth[i] = samples[i].truth;
                lost = lost || samples[i].lost;
                i++;
            }
            int before = start > 0 ? samples[start - 1].known : UNKNOWN;
            int after = i < samples.length ? samples[i].known : UNKNOWN;
            // only fill from a stretch at the end, not one point
            boolean atEnd = start == 0 || i == samples.length;
            if (lost && atEnd && !(isBacked(samples, start - 1, -1) && isBacked(samples, i, 1))) {
                throw new UncalculableException(UncalculableException.TOO_BIG);
            }
            if (before == UNKNOWN && after == UNKNOWN || before != UNKNOWN && after != UNKNOWN && before != after) {
                if (lost) {
                    throw new UncalculableException(UncalculableException.TOO_BIG);
                }
                continue;
            }
            boolean fill = (before == UNKNOWN ? after : before) == TRUE;
            for (int k = start; k < i; k++) {
                truth[k] = fill;
            }
        }
        return truth;
    }

    // true if past the search or next sample agrees
    private static boolean isBacked(Sample[] samples, int index, int direction) {
        if (index < 0 || index >= samples.length) {
            return true;
        }
        for (int further = index + direction; further >= 0 && further < samples.length; further += direction) {
            if (samples[further].known != UNKNOWN) {
                return samples[further].known == samples[index].known;
            }
        }
        return false;
    }

    // truth at sample + whether its just rounding
    private Sample sampleAt(double point, boolean atRoot) {
        context.setAbsorbed(false);
        context.setTolerated(false);
        context.spendStep();
        Context at = context.withVariable(variable, point);
        Value value;
        try {
            value = condition.evaluate(at);
        } catch (UndefinedException e) {
            return new Sample(false, FALSE, false);
        } catch (OverflowException e) {
            // overflow = lost value, rest decides
            lostInSample = true;
            int known = partTruth(condition, at, atRoot);
            return new Sample(false, known, true);
        }
        if (!(value instanceof BooleanValue)) {
            throw new UndecidableException();
        }
        boolean truth = ((BooleanValue) value).getValue();
        if (!context.isAbsorbed() && (atRoot || !context.isTolerated())) {
            return new Sample(truth, truth ? TRUE : FALSE, false);
        }
        lostInSample = false;
        int known = partTruth(condition, at, atRoot);
        return new Sample(truth, known, lostInSample);
    }

    private int partTruth(Node node, Context at, boolean atRoot) {
        if (node instanceof UnaryNode && ((UnaryNode) node).getOp() == UnaryNode.Op.NOT) {
            int inner = partTruth(((UnaryNode) node).getOperand(), at, atRoot);
            return inner == UNKNOWN ? UNKNOWN : TRUE - inner;
        }
        if (node instanceof BinaryNode && node.isCondition()) {
            BinaryNode binary = (BinaryNode) node;
            int p = partTruth(binary.getLeft(), at, atRoot);
            int q = partTruth(binary.getRight(), at, atRoot);
            switch (binary.getOp()) {
                case AND:
                    return p == FALSE || q == FALSE ? FALSE : (p == TRUE && q == TRUE ? TRUE : UNKNOWN);
                case OR:
                    return p == TRUE || q == TRUE ? TRUE : (p == FALSE && q == FALSE ? FALSE : UNKNOWN);
                case IMPLIES:
                    return p == FALSE || q == TRUE ? TRUE : (p == TRUE && q == FALSE ? FALSE : UNKNOWN);
                default:
                    if (p == UNKNOWN || q == UNKNOWN) {
                        return UNKNOWN;
                    }
                    return (binary.getOp() == BinaryNode.Op.IFF) == (p == q) ? TRUE : FALSE;
            }
        }
        context.setAbsorbed(false);
        context.setTolerated(false);
        try {
            Value value = node.evaluate(at);
            if (!(value instanceof BooleanValue)) {
                return UNKNOWN;
            }
            boolean tolerated = context.isTolerated();
            if (context.isAbsorbed() && isRoundingOnly(node, at)) {
                lostInSample = true;
                return UNKNOWN;
            }
            if (tolerated && !atRoot) {
                return UNKNOWN;
            }
            return ((BooleanValue) value).getValue() ? TRUE : FALSE;
        } catch (OverflowException e) {
            lostInSample = true;
            return UNKNOWN;
        } catch (BudgetExceededException | UndecidableException e) {
            throw e;
        } catch (UncalculableException e) {
            return UNKNOWN;
        }
    }

    // lost value only matters if sides are that close
    private boolean isRoundingOnly(Node node, Context at) {
        if (!(node instanceof RelationNode)) {
            return true;
        }
        RelationNode relation = (RelationNode) node;
        List<Node> operands = relation.getOperands();
        for (int i = 0; i + 1 < operands.size(); i++) {
            if (isSetOperator(relation.getOperators().get(i))) {
                continue;
            }
            double a = numberAt(operands.get(i), at);
            double b = numberAt(operands.get(i + 1), at);
            if (!isFinite(a) || !isFinite(b)) {
                return true;
            }
            double scale = Math.max(largestPart(operands.get(i), at), largestPart(operands.get(i + 1), at));
            if (Math.abs(a - b) <= NOISE * scale) {
                return true;
            }
        }
        return false;
    }

    private static double numberAt(Node node, Context at) {
        try {
            Value value = node.evaluate(at);
            return value instanceof NumberValue ? ((NumberValue) value).getValue() : Double.NaN;
        } catch (BudgetExceededException | UndecidableException e) {
            throw e;
        } catch (UncalculableException e) {
            return Double.NaN;
        }
    }

    public boolean isTrueSomewhere() {
        return find(true, REAL, 0) != NOT_FOUND;
    }

    public boolean isTrueEverywhere() {
        return find(false, REAL, 0) == NOT_FOUND;
    }

    // over Q a root may not be a member, undecidable
    public boolean isTrueSomewhereRational() {
        return decidedWithoutRoots(find(true, REAL, 0)) == FOUND;
    }

    public boolean isTrueEverywhereRational() {
        return decidedWithoutRoots(find(false, REAL, 0)) == NOT_FOUND;
    }

    public boolean isTrueForSomeInteger(boolean naturalOnly, int range) {
        return find(true, naturalOnly ? NATURAL : INTEGER, range) != NOT_FOUND;
    }

    public boolean isTrueForEveryInteger(boolean naturalOnly, int range) {
        return find(false, naturalOnly ? NATURAL : INTEGER, range) == NOT_FOUND;
    }

    public boolean isTrueForSome(List<Double> points) {
        return !noMemberGives(points, TRUE);
    }

    public boolean isTrueForEvery(List<Double> points) {
        return noMemberGives(points, FALSE);
    }

    // true if checked every member and none matched
    private boolean noMemberGives(List<Double> points, int wanted) {
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

    private static int decidedWithoutRoots(int result) {
        if (result == FOUND_AT_ROOT) {
            throw new UndecidableException();
        }
        return result;
    }

    // cheap points, then roots, then far scan to 1e300
    private int find(boolean truth, int mode, int range) {
        int wanted = truth ? TRUE : FALSE;
        List<Double> points = mode == REAL ? cheapPoints(true) : integerPoints(mode == NATURAL, range);
        for (double point : points) {
            if (verdictAt(point) == wanted) {
                return FOUND;
            }
        }
        List<Double> rootValues = sortedRootValues();
        for (Root root : getRoots()) {
            int result = checkRoot(root.value, root.pair, wanted, mode, probeOffset(root.value, rootValues));
            if (result != NOT_FOUND) {
                return result;
            }
        }
        // might only hold between close roots
        for (double midpoint : mode == REAL ? rootMidpoints() : new ArrayList<Double>()) {
            if (verdictAt(midpoint) == wanted) {
                return FOUND;
            }
        }
        int result = scanFar(wanted, mode);
        // nested quantifier: close call might hold elsewhere
        boolean nestedMiss = nearMiss && !context.getVariableValues().isEmpty();
        if (result == NOT_FOUND && (skippedRoot || !sawVerdict || undersampled || nestedMiss)) {
            throw new UndecidableException();
        }
        return result;
    }

    // real root: 0 sides are expected, dont skip
    private int checkRoot(double root, Pair pair, int wanted, int mode, double offset) {
        return checkRoot(root, pair, wanted, mode, offset, false);
    }

    // exact meet at domain edge counts
    private int checkRoot(double root, Pair pair, int wanted, int mode, double offset, boolean edgeRoot) {
        boolean genuine = edgeRoot || isGenuineRoot(pair, root, offset);
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

    // close roots, might miss it on the grid
    private List<Double> rootMidpoints() {
        List<Double> rootValues = sortedRootValues();
        List<Double> midpoints = new ArrayList<>();
        for (int i = 0; i + 1 < rootValues.size(); i++) {
            midpoints.add((rootValues.get(i) + rootValues.get(i + 1)) / 2);
        }
        return midpoints;
    }

    // distinct roots sorted
    private List<Double> sortedRootValues() {
        Set<Double> distinct = new HashSet<>(boundaryRoots());
        List<Double> values = new ArrayList<>(distinct);
        Collections.sort(values);
        return values;
    }

    // probe 1e-4 of root size, max 1/4 to next root
    private static double probeOffset(double root, List<Double> rootValues) {
        double offset = PROBE * Math.max(1, Math.abs(root));
        int index = Collections.binarySearch(rootValues, root);
        if (index < 0) {
            return offset;
        }
        if (index > 0) {
            offset = Math.min(offset, (root - rootValues.get(index - 1)) / NEIGHBOUR_SHARE);
        }
        if (index + 1 < rootValues.size()) {
            offset = Math.min(offset, (rootValues.get(index + 1) - root) / NEIGHBOUR_SHARE);
        }
        return Math.max(offset, MIN_PROBE * Math.max(1, Math.abs(root)));
    }

    private int rootVerdictAt(double point, boolean genuine) {
        int reliability = reliabilityAt(point, genuine);
        if (reliability == OUT_OF_RANGE && genuine) {
            skippedRoot = true;
        }
        return reliability == RELIABLE ? verdict(point, genuine) : SKIP;
    }

    // sides cross/meet w/ gaps, nothing lost to rounding
    private boolean isGenuineRoot(Pair pair, double root, double offset) {
        context.setAbsorbed(false);
        double here = difference(pair, root);
        double below = difference(pair, root - offset);
        double above = difference(pair, root + offset);
        if (context.isAbsorbed() || !isFinite(below) || !isFinite(above)) {
            return false;
        }
        boolean touching = here == 0 || Math.abs(here) <= TOUCHING * Math.min(Math.abs(below), Math.abs(above));
        return below * above < 0 || (touching && Math.abs(below) >= TINY && Math.abs(above) >= TINY);
    }

    // scan to 1e300 + dips that might cross 0
    private int scanFar(int wanted, int mode) {
        List<Pair> pairs = numericPairs();
        if (pairs.isEmpty()) {
            return NOT_FOUND;
        }
        boolean dips = context.getVariableValues().isEmpty();
        List<Double> found = new ArrayList<>(sortedRootValues());
        double[][] last = new double[2][pairs.size()];
        double[][] beforeLast = new double[2][pairs.size()];
        double lastSize = 0;
        double beforeLastSize = 0;
        for (int k = 0; k <= SCAN_POINTS; k++) {
            double size = EquationSolver.SEARCH_END * Math.pow(10, k / (double) POINTS_PER_DECADE);
            for (int side = 0; side < 2; side++) {
                double sign = side == 0 ? 1 : -1;
                for (int i = 0; i < pairs.size(); i++) {
                    double d = scanValue(pairs.get(i), sign * size);
                    double before = last[side][i];
                    double twoBefore = beforeLast[side][i];
                    beforeLast[side][i] = before;
                    last[side][i] = d;
                    if (k > 0 && isDomainEdge(before, d)) {
                        // side might just hit the other at domain edge
                        double edge = edgeBetween(pairs.get(i), sign * lastSize, before, sign * size);
                        int result = checkRoot(edge, pairs.get(i), wanted, mode, PROBE * Math.max(1, Math.abs(edge)),
                                scanValue(pairs.get(i), edge) == 0);
                        if (result != NOT_FOUND) {
                            return result;
                        }
                    }
                    if (dips && k >= 2 && isDip(twoBefore, before, d)) {
                        int result = checkDip(pairs.get(i), sign * beforeLastSize, twoBefore, sign * size,
                                wanted, mode);
                        if (result != NOT_FOUND) {
                            return result;
                        }
                    }
                    if (k == 0 || !isFinite(d) || !isFinite(before) || before == 0 || before * d > 0) {
                        continue;
                    }
                    double root = d == 0 ? sign * size : bisect(pairs.get(i), sign * lastSize, sign * size, before);
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
    private int checkFarRoot(double root, Pair pair, int wanted, int mode, List<Double> found) {
        double nearest = Double.NaN;
        for (double other : found) {
            if (other != root && (Double.isNaN(nearest) || Math.abs(other - root) < Math.abs(nearest - root))) {
                nearest = other;
            }
        }
        found.add(root);
        double offset = PROBE * Math.max(1, Math.abs(root));
        if (!Double.isNaN(nearest)) {
            offset = Math.max(Math.min(offset, Math.abs(root - nearest) / NEIGHBOUR_SHARE),
                    MIN_PROBE * Math.max(1, Math.abs(root)));
        }
        int result = checkRoot(root, pair, wanted, mode, offset);
        if (result == NOT_FOUND && mode == REAL && !Double.isNaN(nearest)
                && verdictAt((root + nearest) / 2) == wanted) {
            return FOUND;
        }
        return result;
    }

    // left - right for far scans, NaN/inf when unknown
    private double scanValue(Pair pair, double point) {
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
        if (Double.isInfinite(d) || context.isAbsorbed() || pair.isNoise()) {
            return Double.POSITIVE_INFINITY;
        }
        return d;
    }

    private static boolean isDomainEdge(double before, double now) {
        return Double.isNaN(before) && isFinite(now) || isFinite(before) && Double.isNaN(now);
    }

    // defined end of the edge
    private double edgeBetween(Pair pair, double from, double dFrom, double to) {
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

    // scan past the search end for sign changes
    private boolean changesBeyond(double sign, List<Double> roots, int limit) {
        boolean changes = false;
        for (Pair pair : numericPairs()) {
            double known = Double.POSITIVE_INFINITY;
            double knownPoint = 0;
            double before = Double.POSITIVE_INFINITY;
            double twoBefore = Double.POSITIVE_INFINITY;
            double lastPoint = 0;
            double beforeLastPoint = 0;
            for (int k = 0; k <= SCAN_POINTS && roots.size() <= limit; k++) {
                double point = sign * EquationSolver.SEARCH_END * Math.pow(10, k / (double) POINTS_PER_DECADE);
                double d = scanValue(pair, point);
                if (d != 0 && isFinite(d) && Values.areAlmostEqual(pair.lastLeft, pair.lastRight)) {
                    // within tol only rounding shows
                    d = Double.POSITIVE_INFINITY;
                }
                if (Double.isInfinite(d)) {
                    twoBefore = before;
                    before = d;
                    continue;
                }
                if (k > 0 && !Double.isInfinite(known)) {
                    if (isFinite(d) && isFinite(known) && Math.signum(d) != Math.signum(known)) {
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
                    if (isFinite(lowest[1]) && lowest[1] <= 0) {
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
    private void addCheckedRoot(List<Double> roots, Pair found, double root) {
        context.spendStep();
        Pair pair = found.whole == null ? found : found.whole;
        try {
            Context at = context.withVariable(variable, root);
            double a = pair.leftAt(at);
            double b = pair.rightAt(at);
            if (isFinite(a) && isFinite(b) && Values.areAlmostEqual(a, b)) {
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

    // roots past range out to 1e300, null if too many
    public List<Double> rootsBeyond(int limit) {
        List<Double> roots = new ArrayList<>();
        changesBeyond(-1, roots, limit);
        changesBeyond(1, roots, limit);
        if (roots.size() > limit) {
            return null;
        }
        Collections.sort(roots);
        return roots;
    }

    // 3 same sign vals, middle closest to 0
    private static boolean isDip(double first, double middle, double last) {
        return isFinite(first) && isFinite(middle) && isFinite(last) && first * middle > 0 && middle * last > 0
                && Math.abs(middle) < Math.abs(first) && Math.abs(middle) < Math.abs(last);
    }

    // lowest pt of dip, past 0 = two roots
    private int checkDip(Pair pair, double from, double dFrom, double to, int wanted, int mode) {
        double sign = Math.signum(dFrom);
        double[] lowest = EquationSolver.lowest(x -> sign * difference(pair, x), Math.min(from, to),
                Math.max(from, to));
        if (!isFinite(lowest[1]) || lowest[1] > 0) {
            return NOT_FOUND;
        }
        if (verdictAt(lowest[0]) == wanted) {
            return FOUND;
        }
        double first = bisect(pair, from, lowest[0], dFrom);
        double second = bisect(pair, to, lowest[0], difference(pair, to));
        double offset = Math.abs(second - first) / NEIGHBOUR_SHARE;
        for (double root : new double[] {first, second}) {
            int result = checkRoot(root, pair, wanted, mode, Math.min(offset, PROBE * Math.max(1, Math.abs(root))));
            if (result != NOT_FOUND) {
                return result;
            }
        }
        return NOT_FOUND;
    }

    private double outerScale() {
        double scale = 0;
        for (double value : context.getVariableValues()) {
            scale = Math.max(scale, Math.abs(value));
        }
        return scale;
    }

    private double bisect(Pair pair, double low, double high, double dLow) {
        for (int round = 0; round < ROUNDS; round++) {
            double middle = (low + high) / 2;
            double dMiddle = difference(pair, middle);
            if (dMiddle == 0 || !isFinite(dMiddle)) {
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

    private double difference(Pair pair, double point) {
        context.spendStep();
        try {
            return pair.differenceAt(context.withVariable(variable, point));
        } catch (BudgetExceededException | UndecidableException e) {
            throw e;
        } catch (UncalculableException e) {
            return Double.NaN;
        }
    }

    private int verdictAt(double point) {
        return reliabilityAt(point, false) == RELIABLE ? verdict(point, false) : SKIP;
    }

    // lost value proves nothing unless real root
    private int verdict(double point, boolean atRoot) {
        context.setAbsorbed(false);
        boolean truth;
        try {
            truth = isTrueAt(point);
        } catch (OverflowException e) {
            return SKIP;
        }
        if (context.isAbsorbed() && !atRoot) {
            return SKIP;
        }
        sawVerdict = true;
        return truth ? TRUE : FALSE;
    }

    // skip samples that only show rounding
    private int reliabilityAt(double point, boolean atRoot) {
        boolean far = Math.abs(point) > EquationSolver.SEARCH_END;
        Context at = context.withVariable(variable, point);
        for (RelationNode relation : eligibleRelations()) {
            List<Node> operands = relation.getOperands();
            double[] values = new double[operands.size()];
            double[] errors = new double[operands.size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = Double.NaN;
                try {
                    context.setApproximation(0);
                    Value value = operands.get(i).evaluate(at);
                    errors[i] = context.getApproximation();
                    if (value instanceof NumberValue) {
                        values[i] = ((NumberValue) value).getValue();
                        double size = Math.abs(values[i]);
                        if (far && !(size <= HUGE && (size >= TINY || atRoot))) {
                            return OUT_OF_RANGE;
                        }
                    }
                } catch (BudgetExceededException | UndecidableException e) {
                    throw e;
                } catch (UncalculableException e) {
                    if (far) {
                        return OUT_OF_RANGE;
                    }
                }
            }
            for (int i = 0; i + 1 < values.length; i++) {
                double a = values[i];
                double b = values[i + 1];
                if (isSetOperator(relation.getOperators().get(i)) || !isFinite(a) || !isFinite(b)) {
                    continue;
                }
                // x+1 rounds to x far out, equal means nothing
                boolean absorbing = far && !atRoot;
                if (a == b && !absorbing) {
                    continue;
                }
                double gap = Math.abs(a - b);
                if (!atRoot && a != b && Values.areAlmostEqual(a, b)) {
                    return NOISY;
                }
                boolean approximate = operands.get(i).isApproximate() || operands.get(i + 1).isApproximate();
                if (!atRoot && a != b && approximate
                        && Values.areApproximatelyEqual(a, b, Math.max(errors[i], errors[i + 1]))) {
                    return NOISY;
                }
                if (absorbing || !Values.areAlmostEqual(a, b)) {
                    double scale = Math.max(Math.abs(point), outerScale());
                    scale = Math.max(scale, Math.max(largestPart(operands.get(i), at), largestPart(operands.get(i + 1), at)));
                    if (gap <= NOISE * scale) {
                        return NOISY;
                    }
                }
            }
        }
        return RELIABLE;
    }

    // max size in the arithmetic, bounds rounding err
    private static double largestPart(Node node, Context at) {
        double largest = 0;
        try {
            Value value = node.evaluate(at);
            if (value instanceof NumberValue && isFinite(((NumberValue) value).getValue())) {
                largest = Math.abs(((NumberValue) value).getValue());
            }
        } catch (BudgetExceededException | UndecidableException e) {
            throw e;
        } catch (UncalculableException e) {
            return 0;
        }
        if (node instanceof BinaryNode || node instanceof UnaryNode || node instanceof FunctionNode) {
            for (Node child : node.children()) {
                largest = Math.max(largest, largestPart(child, at));
            }
        }
        return largest;
    }

    private List<Double> integerPoints(boolean naturalOnly, int range) {
        Set<Double> points = new LinkedHashSet<>();
        for (int n = naturalOnly ? 0 : -range; n <= range; n++) {
            points.add((double) n);
        }
        for (double far : FAR_INTEGERS) {
            points.add(far);
            points.add(-far);
        }
        for (double value : context.getVariableValues()) {
            double[] near = {Math.floor(value) - 1, Math.floor(value), Math.ceil(value), Math.ceil(value) + 1,
                    -Math.floor(value), -Math.ceil(value)};
            for (double n : near) {
                points.add(n + 0.0);
            }
        }
        List<Double> integers = new ArrayList<>();
        for (double point : points) {
            if (isFinite(point) && (!naturalOnly || point >= 0)) {
                integers.add(point);
            }
        }
        return integers;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    List<Double> boundaryRoots() {
        List<Double> values = new ArrayList<>();
        for (Root root : getRoots()) {
            values.add(root.value);
        }
        return values;
    }

    private List<Double> cheapPoints(boolean wide) {
        List<Double> points = new ArrayList<>();
        int count = (int) Math.round((EquationSolver.SEARCH_END - EquationSolver.SEARCH_START) / step) + 1;
        for (int i = 0; i < count; i++) {
            points.add(EquationSolver.SEARCH_START + i * step);
        }
        for (double point : SPECIAL_POINTS) {
            points.add(point);
        }
        if (wide) {
            for (double point : FAR_POINTS) {
                points.add(point);
            }
            for (double value : context.getVariableValues()) {
                points.add(value);
                points.add(value - 0.5);
                points.add(value + 0.5);
                points.add(value - 1);
                points.add(value + 1);
                points.add(-value);
            }
            // past every outer value so y > 2x works at x=1e6
            double beyond = 1;
            for (double value : context.getVariableValues()) {
                beyond += OUTER_SCALE * Math.abs(value);
            }
            if (!context.getVariableValues().isEmpty() && !Double.isInfinite(beyond)) {
                points.add(beyond);
                points.add(-beyond);
            }
        }
        return points;
    }

    private List<Double> rootPoints() {
        List<Double> points = new ArrayList<>();
        for (Root root : getRoots()) {
            points.add(root.value);
            points.add(root.value - ROOT_OFFSET);
            points.add(root.value + ROOT_OFFSET);
        }
        return points;
    }

    private double[] sortedCandidates() {
        List<Double> points = cheapPoints(false);
        points.addAll(rootPoints());
        points.addAll(rootMidpoints());
        double[] sorted = new double[points.size()];
        for (int i = 0; i < sorted.length; i++) {
            sorted[i] = points.get(i);
        }
        Arrays.sort(sorted);
        int size = 0;
        for (int i = 0; i < sorted.length; i++) {
            if (size == 0 || sorted[i] != sorted[size - 1]) {
                sorted[size] = sorted[i];
                size++;
            }
        }
        return Arrays.copyOf(sorted, size);
    }

    private boolean isTrueAt(double point) {
        context.spendStep();
        Value value;
        try {
            value = condition.evaluate(context.withVariable(variable, point));
        } catch (UndefinedException e) {
            return false;
        }
        if (!(value instanceof BooleanValue)) {
            throw new UndecidableException();
        }
        return ((BooleanValue) value).getValue();
    }

    // {boundary, INCLUDED?} between false and true
    private double[] boundary(double falsePoint, double truePoint) {
        for (int round = 0; round < ROUNDS; round++) {
            double middle = (falsePoint + truePoint) / 2;
            if (isTrueAt(middle)) {
                truePoint = middle;
            } else {
                falsePoint = middle;
            }
        }
        double limit = (falsePoint + truePoint) / 2;
        // domain edge is its own boundary
        boolean domainEdge = isUndefinedAt(falsePoint);
        double snapped = tidy(limit, !domainEdge);
        if (!Double.isNaN(snapped)) {
            return new double[] {snapped, isTrueAt(snapped) ? INCLUDED : 0};
        }
        if (domainEdge && isTrueAt(truePoint)) {
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
        Root nearest = null;
        for (Root root : getRoots()) {
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
    private boolean isNearlyEqualAt(Root root, double point) {
        Context withPoint = context.withVariable(variable, point);
        try {
            double a = root.pair.leftAt(withPoint);
            double b = root.pair.rightAt(withPoint);
            if (!isFinite(a) || !isFinite(b)) {
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

    private List<Root> getRoots() {
        if (roots == null) {
            roots = new ArrayList<>();
            for (RelationNode relation : eligibleRelations()) {
                addRoots(relation);
            }
        }
        return roots;
    }

    private void addRoots(RelationNode relation) {
        List<Node> operands = relation.getOperands();
        List<String> operators = relation.getOperators();
        for (int i = 0; i + 1 < operands.size(); i++) {
            Node left = operands.get(i);
            Node right = operands.get(i + 1);
            if (isSetOperator(operators.get(i))) {
                addMembershipRoots(left, right);
                continue;
            }
            for (Pair pair : pairsOf(left, right)) {
                EquationSolver solver = new EquationSolver(left, right, variable, context, step,
                        pair.leftPart, pair.rightPart);
                for (double value : solver.solve()) {
                    roots.add(new Root(value, pair, solver.isStretchEnd(value)));
                }
                undersampled = undersampled || solver.isUndersampled();
                nearMiss = nearMiss || solver.isNearMiss();
            }
        }
    }

    private List<Pair> numericPairs() {
        List<Pair> pairs = new ArrayList<>();
        for (RelationNode relation : eligibleRelations()) {
            List<Node> operands = relation.getOperands();
            for (int i = 0; i + 1 < operands.size(); i++) {
                if (!isSetOperator(relation.getOperators().get(i))) {
                    Node left = operands.get(i);
                    Node right = operands.get(i + 1);
                    pairs.addAll(pairsOf(left, right));
                    if (isZero(right)) {
                        addFactorPairs(pairs, left, right);
                    } else if (isZero(left)) {
                        addFactorPairs(pairs, right, left);
                    }
                }
            }
        }
        return pairs;
    }

    // check each factor of a product separately
    private void addFactorPairs(List<Pair> pairs, Node product, Node zero) {
        List<Node> factors = new ArrayList<>();
        collectFactors(product, factors);
        if (factors.size() > 1) {
            Pair whole = new Pair(product, zero, EquationSolver.WHOLE, EquationSolver.WHOLE);
            for (Node factor : factors) {
                pairs.add(new Pair(factor, zero, EquationSolver.WHOLE, EquationSolver.WHOLE, whole));
            }
        }
    }

    private static void collectFactors(Node node, List<Node> factors) {
        if (node instanceof BinaryNode && ((BinaryNode) node).getOp() == BinaryNode.Op.MULTIPLY) {
            collectFactors(((BinaryNode) node).getLeft(), factors);
            collectFactors(((BinaryNode) node).getRight(), factors);
        } else if (node instanceof BinaryNode && ((BinaryNode) node).getOp() == BinaryNode.Op.DIVIDE) {
            collectFactors(((BinaryNode) node).getLeft(), factors);
        } else if (node instanceof UnaryNode && ((UnaryNode) node).getOp() == UnaryNode.Op.NEGATE) {
            collectFactors(((UnaryNode) node).getOperand(), factors);
        } else {
            factors.add(node);
        }
    }

    private boolean isZero(Node node) {
        Set<String> names = new HashSet<>();
        node.collectFreeVariables(names);
        if (!names.isEmpty()) {
            return false;
        }
        try {
            Value value = node.evaluate(context);
            return value instanceof NumberValue && ((NumberValue) value).getValue() == 0;
        } catch (BudgetExceededException | UndecidableException e) {
            throw e;
        } catch (UncalculableException e) {
            return false;
        }
    }

    // compare nums whole, vectors/sets part by part
    private List<Pair> pairsOf(Node left, Node right) {
        int[] leftShape = shapeOf(left);
        int[] rightShape = shapeOf(right);
        List<Pair> pairs = new ArrayList<>();
        if (leftShape[0] == NUMBER_SHAPE && rightShape[0] == NUMBER_SHAPE) {
            pairs.add(new Pair(left, right, EquationSolver.WHOLE, EquationSolver.WHOLE));
        } else if (leftShape[0] == VECTOR_SHAPE && rightShape[0] == VECTOR_SHAPE) {
            for (int i = 0; i < Math.min(leftShape[1], rightShape[1]); i++) {
                pairs.add(new Pair(left, right, i, i));
            }
        } else if (leftShape[0] == SET_SHAPE && rightShape[0] == SET_SHAPE) {
            if (leftShape[1] * rightShape[1] > MAX_ELEMENT_PAIRS) {
                throw new UncalculableException(UncalculableException.SETS_TOO_LARGE);
            }
            for (int j = 0; j < leftShape[1]; j++) {
                for (int k = 0; k < rightShape[1]; k++) {
                    pairs.add(new Pair(left, right, j, k));
                }
            }
        }
        return pairs;
    }

    // {kind, max size} from a few points
    private int[] shapeOf(Node side) {
        int kind = NUMBER_SHAPE;
        int size = 0;
        for (double point : SHAPE_POINTS) {
            Value value;
            try {
                value = side.evaluate(context.withVariable(variable, point));
            } catch (BudgetExceededException | UndecidableException e) {
                throw e;
            } catch (UncalculableException e) {
                continue;
            }
            if (value instanceof VectorValue) {
                kind = VECTOR_SHAPE;
                size = Math.max(size, ((VectorValue) value).getComponents().size());
            } else if (value instanceof FiniteSetValue) {
                kind = SET_SHAPE;
                size = Math.max(size, ((FiniteSetValue) value).getElements().size());
            } else if (!(value instanceof NumberValue)) {
                kind = OTHER_SHAPE;
            }
        }
        return new int[] {kind, size};
    }

    // rels w/ all vars known after solving (R1)
    private List<RelationNode> eligibleRelations() {
        if (relations == null) {
            relations = new ArrayList<>();
            collectRelations(condition);
        }
        return relations;
    }

    private void collectRelations(Node node) {
        if (node instanceof RelationNode && isSolvable(node)) {
            relations.add((RelationNode) node);
        }
        for (Node child : node.children()) {
            collectRelations(child);
        }
    }

    private boolean isSetOperator(String operator) {
        return operator.equals("\\in") || operator.equals("\\notin") || operator.equals("\\subset");
    }

    private void addMembershipRoots(Node left, Node right) {
        Set<String> names = new HashSet<>();
        right.collectFreeVariables(names);
        if (names.contains(variable)) {
            return;
        }
        try {
            Value set = right.evaluate(context);
            if (set instanceof FiniteSetValue) {
                for (double element : ((FiniteSetValue) set).getElements()) {
                    Node target = new LiteralNode(new NumberValue(element), false);
                    for (double value : new EquationSolver(left, target, variable, context, step).solve()) {
                        roots.add(new Root(value,
                                new Pair(left, target, EquationSolver.WHOLE, EquationSolver.WHOLE), false));
                    }
                }
            }
        } catch (BudgetExceededException | UndecidableException e) {
            throw e;
        } catch (UncalculableException e) {
            // no roots here
        }
    }

    private boolean isSolvable(Node relation) {
        Set<String> names = new HashSet<>();
        relation.collectFreeVariables(names);
        for (String name : names) {
            if (!name.equals(variable) && !context.hasVariable(name)) {
                return false;
            }
        }
        return true;
    }

    private static final class Sample {
        private final boolean truth;
        private final int known;
        private final boolean lost;

        private Sample(boolean truth, int known, boolean lost) {
            this.truth = truth;
            this.known = known;
            this.lost = lost;
        }
    }

    private static final class Root {
        private final double value;
        private final Pair pair;
        // just the end of an equal stretch
        private final boolean stretchEnd;

        private Root(double value, Pair pair, boolean stretchEnd) {
            this.value = value;
            this.pair = pair;
            this.stretchEnd = stretchEnd;
        }
    }

    // compare two sides or one part each
    private static final class Pair {
        private final Node left;
        private final Node right;
        private final int leftPart;
        private final int rightPart;

        // comparison for a factor of a product
        private final Pair whole;

        private Pair(Node left, Node right, int leftPart, int rightPart) {
            this(left, right, leftPart, rightPart, null);
        }

        private Pair(Node left, Node right, int leftPart, int rightPart, Pair whole) {
            this.left = left;
            this.right = right;
            this.leftPart = leftPart;
            this.rightPart = rightPart;
            this.whole = whole;
        }

        private double lastLeft = Double.NaN;
        private double lastRight = Double.NaN;
        private double lastError;

        private double leftAt(Context at) {
            return EquationSolver.partOf(left.evaluate(at), leftPart);
        }

        private double rightAt(Context at) {
            return EquationSolver.partOf(right.evaluate(at), rightPart);
        }

        private double differenceAt(Context at) {
            lastLeft = Double.NaN;
            lastRight = Double.NaN;
            at.setApproximation(0);
            lastLeft = leftAt(at);
            lastRight = rightAt(at);
            lastError = at.getApproximation();
            return lastLeft - lastRight;
        }

        // approx sides equal within error
        private boolean isNoise() {
            return (left.isApproximate() || right.isApproximate()) && lastLeft != lastRight
                    && Values.areApproximatelyEqual(lastLeft, lastRight, lastError);
        }
    }
}
