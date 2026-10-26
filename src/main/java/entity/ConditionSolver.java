package entity;

import entity.node.Node;
import entity.node.RelationNode;
import entity.value.Values;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConditionSolver {
    private static final double GRID_STEP = 0.01;
    private static final double NESTED_STEP = 1;
    private static final double ROOT_OFFSET = 1e-7;
    private static final double SNAP = 1e-6;
    private static final double NEARLY_EQUAL = 1e-8;
    private static final int ROUNDS = 60;
    private static final double[] SPECIAL_POINTS = {0, 0.5, -0.5, 1, -1};
    private static final double[] FAR_POINTS = {1e3, -1e3, 1e6, -1e6};

    private final Node condition;
    private final String variable;
    private final Context context;
    private final double step;
    private List<Root> roots;

    public ConditionSolver(Node condition, String variable, Context context) {
        this.condition = condition;
        this.variable = variable;
        this.context = context;
        this.step = context.getVariableValues().isEmpty() ? GRID_STEP : NESTED_STEP;
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
        for (double point : cheapPoints(true)) {
            if (isTrueAt(point)) {
                return true;
            }
        }
        for (double point : rootPoints()) {
            if (isTrueAt(point)) {
                return true;
            }
        }
        return false;
    }

    public boolean isTrueEverywhere() {
        for (double point : cheapPoints(true)) {
            if (!isTrueAt(point)) {
                return false;
            }
        }
        for (double point : rootPoints()) {
            if (!isTrueAt(point)) {
                return false;
            }
        }
        return true;
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
        try {
            return Values.toBoolean(condition.evaluate(context.withVariable(variable, point)));
        } catch (BudgetExceededException e) {
            throw e;
        } catch (UncalculableException e) {
            return false;
        }
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
        if (Math.abs(point) < 1e-9) {
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
        } catch (BudgetExceededException e) {
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
            collectRoots(condition);
        }
        return roots;
    }

    private void collectRoots(Node node) {
        if (node instanceof RelationNode && isSolvable(node)) {
            List<Node> operands = ((RelationNode) node).getOperands();
            for (int i = 0; i + 1 < operands.size(); i++) {
                Node left = operands.get(i);
                Node right = operands.get(i + 1);
                for (double value : new EquationSolver(left, right, variable, context, step).solve()) {
                    roots.add(new Root(value, left, right));
                }
            }
        }
        for (Node child : node.children()) {
            collectRoots(child);
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
