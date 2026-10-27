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
    private static final double GOLDEN = (Math.sqrt(5) - 1) / 2;
    private static final int GOLDEN_ROUNDS = 100;
    private static final double TOUCHING_SHARE = 0.5;
    private static final double FLAT_BOTTOM = 1e-12;
    private static final int MAX_TOUCHING_SEARCHES = 200;

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
        int touchingSearches = 0;
        for (int i = 0; i < count; i++) {
            if (i > 0 && isNumber(ds[i - 1]) && isNumber(ds[i]) && ds[i - 1] * ds[i] < 0) {
                double root = bisect(xs[i - 1], xs[i], ds[i - 1]);
                if (isNumber(root)) {
                    addSolution(solutions, root);
                }
            }
            if (isNumber(ds[i]) && Math.abs(ds[i]) < ZERO && isSmallerThanNeighbours(ds, i)) {
                addSolution(solutions, xs[i]);
            } else if (touchingSearches < MAX_TOUCHING_SEARCHES && isTouchingCandidate(ds, i)) {
                touchingSearches++;
                double root = touchingRoot(xs[i - 1], xs[i + 1]);
                if (isNumber(root)) {
                    addSolution(solutions, root);
                }
            }
        }
        return solutions;
    }

    // dip that doesnt change sign might still touch 0
    private boolean isTouchingCandidate(double[] ds, int i) {
        if (i == 0 || i == ds.length - 1 || !isNumber(ds[i - 1]) || !isNumber(ds[i]) || !isNumber(ds[i + 1])) {
            return false;
        }
        double a = Math.abs(ds[i - 1]);
        double b = Math.abs(ds[i]);
        double c = Math.abs(ds[i + 1]);
        if (ds[i - 1] * ds[i] <= 0 || ds[i] * ds[i + 1] <= 0 || b > a || b >= c) {
            return false;
        }
        double curvature = a + c - 2 * b;
        double parabolaLowest = b - (c - a) * (c - a) / (8 * curvature);
        double cornerLowest = (2 * b + Math.min(a, c) - Math.max(a, c)) / 2;
        return Math.min(parabolaLowest, cornerLowest) < TOUCHING_SHARE * b;
    }

    // golden section for min |diff|, mid of flat bottom
    private double touchingRoot(double low, double high) {
        double a = low;
        double b = high;
        double c = b - GOLDEN * (b - a);
        double d = a + GOLDEN * (b - a);
        double fc = Math.abs(difference(c));
        double fd = Math.abs(difference(d));
        for (int round = 0; round < GOLDEN_ROUNDS && b - a > 1e-15 * Math.max(1, Math.abs(a)); round++) {
            if (!isNumber(fc) || !isNumber(fd)) {
                return Double.NaN;
            }
            if (fc <= fd) {
                b = d;
                d = c;
                fd = fc;
                c = b - GOLDEN * (b - a);
                fc = Math.abs(difference(c));
            } else {
                a = c;
                c = d;
                fc = fd;
                d = a + GOLDEN * (b - a);
                fd = Math.abs(difference(d));
            }
        }
        double best = fc <= fd ? c : d;
        double lowest = Math.min(fc, fd);
        if (!isNumber(lowest) || lowest >= ZERO) {
            return Double.NaN;
        }
        double left = edgeOfBottom(best, low);
        double right = edgeOfBottom(best, high);
        return (left + right) / 2;
    }

    // where |diff| goes past FLAT_BOTTOM
    private double edgeOfBottom(double inside, double outside) {
        double dOutside = Math.abs(difference(outside));
        if (!isNumber(dOutside) || dOutside <= FLAT_BOTTOM) {
            return inside;
        }
        for (int round = 0; round < 60; round++) {
            double middle = (inside + outside) / 2;
            double dMiddle = Math.abs(difference(middle));
            if (!isNumber(dMiddle)) {
                return inside;
            }
            if (dMiddle <= FLAT_BOTTOM) {
                inside = middle;
            } else {
                outside = middle;
            }
        }
        return inside;
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
        // root can sit at edge of a flat bit where diff is exactly 0
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
