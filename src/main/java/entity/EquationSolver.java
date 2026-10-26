package entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EquationSolver {
    public static final double SEARCH_START = -100;
    public static final double SEARCH_END = 100;
    private static final double STEP = 0.01;

    private static final double ZERO = 1e-9;

    private final List<String> left;
    private final List<String> right;
    private final String variable;
    private final Map<String, UserFunction> functions;

    public EquationSolver(List<String> left, List<String> right, String variable,
                          Map<String, UserFunction> functions) {
        this.left = left;
        this.right = right;
        this.variable = variable;
        this.functions = functions;
    }

    public List<Double> solve() {
        int count = (int) Math.round((SEARCH_END - SEARCH_START) / STEP) + 1;
        double[] xs = new double[count];
        double[] ds = new double[count];
        for (int i = 0; i < count; i++) {
            xs[i] = SEARCH_START + i * STEP;
            ds[i] = difference(xs[i]);
        }

        List<Double> solutions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (i > 0 && isNumber(ds[i - 1]) && isNumber(ds[i]) && ds[i - 1] * ds[i] < 0) {
                double root = bisect(xs[i - 1], xs[i], ds[i - 1]);
                if (isNumber(root)) {
                    addSolution(solutions, root);
                }
            }
            if (isNumber(ds[i]) && Math.abs(ds[i]) < ZERO && isSmallerThanNeighbours(ds, i)) {
                addSolution(solutions, xs[i]);
            }
        }
        return solutions;
    }

    public boolean isAlwaysTrue() {
        double[] testValues = {-7.3, -1.1, 0, 0.5, 2.9, 13.7};
        for (double x : testValues) {
            double d = difference(x);
            if (!isNumber(d) || Math.abs(d) > ZERO) {
                return false;
            }
        }
        return true;
    }

    private double difference(double x) {
        Map<String, Double> values = new HashMap<>();
        values.put(variable, x);
        double leftValue = new MathEvaluator(left, values, functions).evaluate();
        double rightValue = new MathEvaluator(right, values, functions).evaluate();
        return leftValue - rightValue;
    }

    private double bisect(double low, double high, double dLow) {
        for (int round = 0; round < 60; round++) {
            double middle = (low + high) / 2;
            double dMiddle = difference(middle);
            if (!isNumber(dMiddle)) {
                return Double.NaN;
            }
            if (dLow * dMiddle <= 0) {
                high = middle;
            } else {
                low = middle;
                dLow = dMiddle;
            }
        }
        double root = (low + high) / 2;
        double dRoot = difference(root);
        if (isNumber(dRoot) && Math.abs(dRoot) < 1e-6) {
            return root;
        }
        return Double.NaN;
    }

    private boolean isSmallerThanNeighbours(double[] ds, int i) {
        double here = Math.abs(ds[i]);
        if (i > 0 && isNumber(ds[i - 1]) && Math.abs(ds[i - 1]) < here) {
            return false;
        }
        if (i < ds.length - 1 && isNumber(ds[i + 1]) && Math.abs(ds[i + 1]) < here) {
            return false;
        }
        return true;
    }

    private void addSolution(List<Double> solutions, double x) {
        if (!solutions.isEmpty()) {
            double last = solutions.get(solutions.size() - 1);
            if (Math.abs(x - last) < 1e-6) {
                return;
            }
        }
        solutions.add(x);
    }

    private boolean isNumber(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
