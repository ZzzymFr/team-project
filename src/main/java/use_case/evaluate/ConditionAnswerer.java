package use_case.evaluate;

import entity.BudgetExceededException;
import entity.ConditionSolver;
import entity.Context;
import entity.EquationSolver;
import entity.Interval;
import entity.UncalculableException;
import entity.UncalculableReason;
import entity.UserFunction;
import entity.node.Node;
import entity.node.RelationNode;
import entity.value.NumberValue;
import entity.value.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

// one condition w/ one unknown -> roots, intervals or excluded points
final class ConditionAnswerer {
    private static final double[] SIDE_PROBES = {0.5, -7.3, 2.9};
    private static final int MAX_ROOTS_BEYOND = 10;

    private final EvaluateOutputBoundary presenter;
    private final Node node;
    private final String variable;
    private final Map<String, UserFunction> functions;
    private final int masks;

    ConditionAnswerer(EvaluateOutputBoundary presenter, Node node, String variable,
                      Map<String, UserFunction> functions, int masks) {
        this.presenter = presenter;
        this.node = node;
        this.variable = variable;
        this.functions = functions;
        this.masks = masks;
    }

    void answer() {
        if (!isSingleEquation()) {
            solveInequality();
            return;
        }
        boolean excluding = ((RelationNode) node).getOperators().get(0).equals("\\neq");
        List<Node> sides = ((RelationNode) node).getOperands();
        if (!hasNumberSides(sides)) {
            // sets/vectors need the condition solver
            solveInequality();
            return;
        }
        List<Double> all = new ArrayList<>();
        boolean stretch = false;
        for (int mask = 0; mask < masks; mask++) {
            EquationSolver solver = new EquationSolver(sides.get(0), sides.get(1), variable, context(mask));
            if (solver.isAlwaysTrue()) {
                if (excluding) {
                    presenter.prepareSolutionsView(variable, new ArrayList<Double>());
                } else {
                    presenter.prepareAlwaysTrueView(variable);
                }
                return;
            }
            all.addAll(solver.solve());
            if (solver.isUndersampled()) {
                throw new UncalculableException(UncalculableReason.TOO_FAST);
            }
            stretch = stretch || solver.hasStretch();
        }
        List<Double> solutions = Roots.sortedDistinct(all);
        if (stretch && !excluding) {
            // equal on a whole stretch, intervals only
            solveInequality();
        } else if (!excluding) {
            presenter.prepareSolutionsView(variable, solutions.isEmpty() ? rootsBeyond() : withRootsBeyond(solutions));
        } else if (masks > 1) {
            excludeWithSigns();
        } else {
            excludeUndefinedPoints(solutions);
        }
    }

    private Context context(int mask) {
        return new Context(functions).withSigns(mask);
    }

    private boolean isSingleEquation() {
        if (!(node instanceof RelationNode)) {
            return false;
        }
        List<String> operators = ((RelationNode) node).getOperators();
        return operators.size() == 1 && (operators.get(0).equals("=") || operators.get(0).equals("\\neq")
                || operators.get(0).equals("\\approx"));
    }

    private boolean hasNumberSides(List<Node> sides) {
        Context context = new Context(functions);
        for (double point : SIDE_PROBES) {
            for (Node side : sides) {
                try {
                    Value value = side.evaluate(context.withVariable(variable, point));
                    if (!(value instanceof NumberValue)) {
                        return false;
                    }
                } catch (UncalculableException e) {
                    // undefined here, try another point
                }
            }
        }
        return true;
    }

    // no root in range -> use roots past it if few (R31)
    private List<Double> rootsBeyond() {
        List<Double> all = new ArrayList<>();
        for (int mask = 0; mask < masks; mask++) {
            List<Double> roots = new ConditionSolver(node, variable, context(mask)).rootsBeyond(MAX_ROOTS_BEYOND);
            if (roots == null) {
                throw new UncalculableException(UncalculableReason.BEYOND_RANGE);
            }
            all.addAll(roots);
        }
        List<Double> roots = Roots.sortedDistinct(all);
        if (roots.size() > MAX_ROOTS_BEYOND) {
            throw new UncalculableException(UncalculableReason.TOO_MANY_SOLUTIONS);
        }
        return roots;
    }

    // in-range roots + the few past range; endless past range w/ few in range = too many
    private List<Double> withRootsBeyond(List<Double> solutions) {
        if (solutions.size() > MAX_ROOTS_BEYOND) {
            // lots in range = likely periodic, far scan too sparse
            return solutions;
        }
        List<Double> all = new ArrayList<>(solutions);
        for (int mask = 0; mask < masks; mask++) {
            List<Double> beyond;
            try {
                beyond = new ConditionSolver(node, variable, context(mask)).rootsBeyond(MAX_ROOTS_BEYOND);
            } catch (BudgetExceededException e) {
                // far scan too costly (sums to huge n), keep what we have
                return solutions;
            }
            if (beyond == null) {
                // sin(x/100)=0: only 0 in range, endless past it
                throw new UncalculableException(UncalculableReason.TOO_MANY_SOLUTIONS);
            }
            for (double root : beyond) {
                if (Math.abs(root) > EquationSolver.SEARCH_END && !Roots.containsSame(all, root)) {
                    all.add(root);
                }
            }
        }
        if (all.size() > MAX_ROOTS_BEYOND) {
            throw new UncalculableException(UncalculableReason.TOO_MANY_SOLUTIONS);
        }
        Collections.sort(all);
        return all;
    }

    // exclude where a side is undefined too
    private void excludeUndefinedPoints(List<Double> solutions) {
        if (masks > 1) {
            throw new UncalculableException(UncalculableReason.PLUS_MINUS_HERE);
        }
        ConditionSolver solver = new ConditionSolver(node, variable, new Context(functions));
        if (solutions.isEmpty()) {
            List<Double> beyond = rootsBeyond();
            if (!beyond.isEmpty()) {
                // x != 200: true in range, false only past it
                List<Interval> inRange = solver.solveInRange();
                if (inRange.size() != 1 || inRange.get(0).getLow() != Double.NEGATIVE_INFINITY
                        || inRange.get(0).getHigh() != Double.POSITIVE_INFINITY) {
                    throw new UncalculableException(UncalculableReason.BEYOND_RANGE);
                }
                presenter.prepareExcludedView(variable, beyond);
                return;
            }
        }
        List<Interval> intervals = solver.solve();
        if (!solutions.isEmpty() && isEverythingBut(intervals, solutions)) {
            presenter.prepareExcludedView(variable, solutions);
        } else {
            showIntervals(intervals);
        }
    }

    // \pm: exclude points from every sign choice
    private void excludeWithSigns() {
        List<Double> all = new ArrayList<>();
        for (int mask = 0; mask < masks; mask++) {
            List<Interval> intervals = new ConditionSolver(node, variable, context(mask)).solve();
            if (intervals.isEmpty() || intervals.get(0).getLow() != Double.NEGATIVE_INFINITY
                    || intervals.get(intervals.size() - 1).getHigh() != Double.POSITIVE_INFINITY) {
                throw new UncalculableException(UncalculableReason.PLUS_MINUS_HERE);
            }
            List<Double> points = new ArrayList<>();
            for (int i = 1; i < intervals.size(); i++) {
                points.add(intervals.get(i).getLow());
            }
            if (!isEverythingBut(intervals, points)) {
                throw new UncalculableException(UncalculableReason.PLUS_MINUS_HERE);
            }
            all.addAll(points);
        }
        presenter.prepareExcludedView(variable, Roots.sortedDistinct(all));
    }

    private static boolean isEverythingBut(List<Interval> intervals, List<Double> points) {
        if (intervals.size() != points.size() + 1) {
            return false;
        }
        for (int i = 0; i < intervals.size(); i++) {
            Interval interval = intervals.get(i);
            double low = i == 0 ? Double.NEGATIVE_INFINITY : points.get(i - 1);
            double high = i == points.size() ? Double.POSITIVE_INFINITY : points.get(i);
            if (interval.isLowIncluded() || interval.isHighIncluded() || !Roots.isSame(interval.getLow(), low)
                    || !Roots.isSame(interval.getHigh(), high)) {
                return false;
            }
        }
        return true;
    }

    private void solveInequality() {
        if (masks > 1) {
            throw new UncalculableException(UncalculableReason.PLUS_MINUS_HERE);
        }
        showIntervals(new ConditionSolver(node, variable, new Context(functions)).solve());
    }

    private void showIntervals(List<Interval> intervals) {
        if (intervals.size() == 1 && intervals.get(0).getLow() == Double.NEGATIVE_INFINITY
                && intervals.get(0).getHigh() == Double.POSITIVE_INFINITY) {
            presenter.prepareAlwaysTrueView(variable);
        } else {
            List<IntervalOutputData> output = new ArrayList<>();
            for (Interval interval : intervals) {
                output.add(OutputDataMapper.of(interval));
            }
            presenter.prepareIntervalsView(variable, output);
        }
    }
}
