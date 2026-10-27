package entity;

import entity.node.LiteralNode;
import entity.node.Node;
import entity.node.QuantifierNode;
import entity.node.RelationNode;
import entity.value.BooleanValue;
import entity.value.FiniteSetValue;
import entity.value.NumberValue;
import entity.value.Value;
import entity.value.Values;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
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
    private static final int POINTS_PER_DECADE = 3;
    private static final int SCAN_POINTS = 3 * 298;
    private static final double TINY = 1e-300;
    private static final double HUGE = 1e300;
    private static final double ABSOLUTE_BAND = 1e-9;
    private static final double NOISE = 1e-12;
    private static final int REAL = 0;
    private static final int INTEGER = 1;
    private static final int NATURAL = 2;
    private static final int TRUE = 1;
    private static final int FALSE = 0;
    private static final int SKIP = -1;
    private static final int FOUND = 0;
    private static final int FOUND_AT_ROOT = 1;
    private static final int NOT_FOUND = 2;

    private final Node condition;
    private final String variable;
    private final Context context;
    private final double step;
    private List<Root> roots;
    private List<RelationNode> relations;
    private boolean sawVerdict;

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

    public List<Interval> solve() {
        double[] points = sortedCandidates();
        boolean[] truth = new boolean[points.length];
        for (int i = 0; i < points.length; i++) {
            truth[i] = isTrueAt(points[i]);
        }

        List<Interval> intervals = new ArrayList<>();
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

            double low = Double.NEGATIVE_INFINITY;
            boolean lowIncluded = false;
            if (start > 0) {
                low = boundary(points[start - 1], points[start]);
                lowIncluded = isTrueAt(low);
            }
            double high = Double.POSITIVE_INFINITY;
            boolean highIncluded = false;
            if (end < points.length - 1) {
                high = boundary(points[end + 1], points[end]);
                highIncluded = isTrueAt(high);
            }
            addInterval(intervals, new Interval(low, lowIncluded, high, highIncluded));
            i++;
        }
        return intervals;
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
        for (double point : points) {
            if (isTrueAt(point)) {
                return true;
            }
        }
        return false;
    }

    public boolean isTrueForEvery(List<Double> points) {
        for (double point : points) {
            if (!isTrueAt(point)) {
                return false;
            }
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
        for (Root root : getRoots()) {
            int result = checkRoot(root.value, wanted, mode);
            if (result != NOT_FOUND) {
                return result;
            }
        }
        int result = scanFar(wanted, mode);
        if (result == NOT_FOUND && !sawVerdict) {
            throw new UndecidableException();
        }
        return result;
    }

    private int checkRoot(double root, int wanted, int mode) {
        double offset = PROBE * Math.max(1, Math.abs(root));
        if (mode == REAL) {
            if (verdictAt(root - offset) == wanted || verdictAt(root + offset) == wanted) {
                return FOUND;
            }
            return verdictAt(root) == wanted ? FOUND_AT_ROOT : NOT_FOUND;
        }
        double[] near = {Math.floor(root) - 1, Math.floor(root), Math.ceil(root), Math.ceil(root) + 1,
                Math.floor(root - offset), Math.ceil(root + offset)};
        for (double n : near) {
            if ((mode == INTEGER || n >= 0) && verdictAt(n) == wanted) {
                return FOUND;
            }
        }
        return NOT_FOUND;
    }

    // sign changes 3 per decade out to 1e300
    private int scanFar(int wanted, int mode) {
        List<Node[]> pairs = numericPairs();
        if (pairs.isEmpty()) {
            return NOT_FOUND;
        }
        double[][] last = new double[2][pairs.size()];
        double lastSize = 0;
        for (int k = 0; k <= SCAN_POINTS; k++) {
            double size = EquationSolver.SEARCH_END * Math.pow(10, k / (double) POINTS_PER_DECADE);
            for (int side = 0; side < 2; side++) {
                double sign = side == 0 ? 1 : -1;
                for (int i = 0; i < pairs.size(); i++) {
                    double d = difference(pairs.get(i), sign * size);
                    double before = last[side][i];
                    last[side][i] = d;
                    if (k == 0 || !isFinite(d) || !isFinite(before) || before == 0 || before * d > 0) {
                        continue;
                    }
                    double root = d == 0 ? sign * size : bisect(pairs.get(i), sign * lastSize, sign * size, before);
                    int result = checkRoot(root, wanted, mode);
                    if (result != NOT_FOUND) {
                        return result;
                    }
                }
            }
            lastSize = size;
        }
        return NOT_FOUND;
    }

    private double bisect(Node[] pair, double low, double high, double dLow) {
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

    private double difference(Node[] pair, double point) {
        context.spendStep();
        Context at = context.withVariable(variable, point);
        try {
            Value left = pair[0].evaluate(at);
            Value right = pair[1].evaluate(at);
            if (!(left instanceof NumberValue) || !(right instanceof NumberValue)) {
                return Double.NaN;
            }
            return ((NumberValue) left).getValue() - ((NumberValue) right).getValue();
        } catch (BudgetExceededException | UndecidableException e) {
            throw e;
        } catch (UncalculableException e) {
            return Double.NaN;
        }
    }

    private int verdictAt(double point) {
        if (isUnreliableAt(point)) {
            return SKIP;
        }
        sawVerdict = true;
        return isTrueAt(point) ? TRUE : FALSE;
    }

    // skip samples that only show rounding
    private boolean isUnreliableAt(double point) {
        boolean far = Math.abs(point) > EquationSolver.SEARCH_END;
        double scale = Math.abs(point);
        for (double value : context.getVariableValues()) {
            scale = Math.max(scale, Math.abs(value));
        }
        Context at = context.withVariable(variable, point);
        for (RelationNode relation : eligibleRelations()) {
            List<Node> operands = relation.getOperands();
            double[] values = new double[operands.size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = Double.NaN;
                try {
                    Value value = operands.get(i).evaluate(at);
                    if (value instanceof NumberValue) {
                        values[i] = ((NumberValue) value).getValue();
                        double size = Math.abs(values[i]);
                        if (far && !(size >= TINY && size <= HUGE)) {
                            return true;
                        }
                    }
                } catch (BudgetExceededException | UndecidableException e) {
                    throw e;
                } catch (UncalculableException e) {
                    if (far) {
                        return true;
                    }
                }
            }
            for (int i = 0; i + 1 < values.length; i++) {
                double a = values[i];
                double b = values[i + 1];
                if (isSetOperator(relation.getOperators().get(i)) || !isFinite(a) || !isFinite(b) || a == b) {
                    continue;
                }
                double gap = Math.abs(a - b);
                if (gap <= ABSOLUTE_BAND && Math.max(Math.abs(a), Math.abs(b)) < 1) {
                    return true;
                }
                if (far && !Values.areAlmostEqual(a, b) && gap <= NOISE * scale) {
                    return true;
                }
            }
        }
        return false;
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
        } catch (BudgetExceededException | UndecidableException e) {
            throw e;
        } catch (UncalculableException e) {
            return false;
        }
        if (!(value instanceof BooleanValue)) {
            throw new UndecidableException();
        }
        return ((BooleanValue) value).getValue();
    }

    private double boundary(double falsePoint, double truePoint) {
        for (int round = 0; round < ROUNDS; round++) {
            double middle = (falsePoint + truePoint) / 2;
            if (isTrueAt(middle)) {
                truePoint = middle;
            } else {
                falsePoint = middle;
            }
        }
        return tidy((falsePoint + truePoint) / 2);
    }

    private double tidy(double point) {
        Root nearest = null;
        for (Root root : getRoots()) {
            if (nearest == null || Math.abs(root.value - point) < Math.abs(nearest.value - point)) {
                nearest = root;
            }
        }
        if (nearest != null) {
            double distance = Math.abs(nearest.value - point);
            if (distance < SNAP || isNearlyEqualAt(nearest, point)) {
                return nearest.value;
            }
        }
        if (Math.abs(point) <= ZERO_SNAP) {
            return 0;
        }
        return new BigDecimal(point).round(new MathContext(8)).doubleValue();
    }

    // inside tol of a root the root is the real boundary
    private boolean isNearlyEqualAt(Root root, double point) {
        Context withPoint = context.withVariable(variable, point);
        try {
            double a = Values.toNumber(root.left.evaluate(withPoint));
            double b = Values.toNumber(root.right.evaluate(withPoint));
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
            for (double value : new EquationSolver(left, right, variable, context, step).solve()) {
                roots.add(new Root(value, left, right));
            }
        }
    }

    private List<Node[]> numericPairs() {
        List<Node[]> pairs = new ArrayList<>();
        for (RelationNode relation : eligibleRelations()) {
            List<Node> operands = relation.getOperands();
            for (int i = 0; i + 1 < operands.size(); i++) {
                if (!isSetOperator(relation.getOperators().get(i))) {
                    pairs.add(new Node[] {operands.get(i), operands.get(i + 1)});
                }
            }
        }
        return pairs;
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
                        roots.add(new Root(value, left, target));
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

    private static final class Root {
        private final double value;
        private final Node left;
        private final Node right;

        private Root(double value, Node left, Node right) {
            this.value = value;
            this.left = left;
            this.right = right;
        }
    }
}
