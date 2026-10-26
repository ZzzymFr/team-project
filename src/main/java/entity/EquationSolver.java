package entity;

import entity.node.Node;
import entity.value.NumberValue;
import entity.value.Value;

import java.util.ArrayList;
import java.util.List;

public class EquationSolver {
    public static final double SEARCH_START = -100;
    public static final double SEARCH_END = 100;
    private static final double STEP = 0.01;

    private static final double ZERO = 1e-9;

    private final Node left;
    private final Node right;
    private final String variable;
    private final Context context;
    private final double step;

    public EquationSolver(Node left, Node right, String variable, Context context) {
        this(left, right, variable, context, STEP);
    }

    public EquationSolver(Node left, Node right, String variable, Context context, double step) {
        this.left = left;
        this.right = right;
        this.variable = variable;
        this.context = context;
        this.step = step;
    }

    public List<Double> solve() {
        int count = (int) Math.round((SEARCH_END - SEARCH_START) / step) + 1;
        double[] xs = new double[count];
        double[] ds = new double[count];
        for (int i = 0; i < count; i++) {
            xs[i] = SEARCH_START + i * step;
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
        Context withX = context.withVariable(variable, x);
        try {
            Value leftValue = left.evaluate(withX);
            Value rightValue = right.evaluate(withX);
            if (!(leftValue instanceof NumberValue) || !(rightValue instanceof NumberValue)) {
                return Double.NaN;
            }
            return ((NumberValue) leftValue).getValue() - ((NumberValue) rightValue).getValue();
        } catch (BudgetExceededException e) {
            throw e;
        } catch (UncalculableException e) {
            return Double.NaN;
        }
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
        double dHigh = difference(high);
        if (isNumber(dHigh) && Math.abs(dHigh) < 1e-6) {
            return high;
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
