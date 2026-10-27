package entity;

import entity.node.Node;
import entity.value.FiniteSetValue;
import entity.value.NumberValue;
import entity.value.Value;
import entity.value.VectorValue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

public class EquationSolver {
    public static final double SEARCH_START = -100;
    public static final double SEARCH_END = 100;
    public static final int WHOLE = -1;
    private static final double STEP = 0.01;

    private static final double ZERO = 1e-9;
    private static final double GOLDEN = (Math.sqrt(5) - 1) / 2;
    private static final int GOLDEN_ROUNDS = 100;
    private static final double TOUCHING_SHARE = 0.5;
    private static final double FLAT_BOTTOM = 1e-12;
    private static final int EDGE_ROUNDS = 50;
    private static final double DEEP = 1e-6;
    // turns too often = grid too coarse
    private static final double MAX_TURNS_PER_POINT = 0.5;
    private static final double TURN_NOISE = 1e-6;

    private final Node left;
    private final Node right;
    private final String variable;
    private final Context context;
    private final double step;
    private final int leftPart;
    private final int rightPart;
    private boolean hasStretch;
    private boolean undersampled;

    public EquationSolver(Node left, Node right, String variable, Context context) {
        this(left, right, variable, context, STEP);
    }

    public EquationSolver(Node left, Node right, String variable, Context context, double step) {
        this(left, right, variable, context, step, WHOLE, WHOLE);
    }

    // solve one part of left = one part of right
    public EquationSolver(Node left, Node right, String variable, Context context, double step,
                          int leftPart, int rightPart) {
        this.left = left;
        this.right = right;
        this.variable = variable;
        this.context = context;
        this.step = step;
        this.leftPart = leftPart;
        this.rightPart = rightPart;
    }

    // number at that part or NaN
    public static double partOf(Value value, int part) {
        if (part == WHOLE) {
            return value instanceof NumberValue ? ((NumberValue) value).getValue() : Double.NaN;
        }
        List<Double> parts;
        if (value instanceof VectorValue) {
            parts = ((VectorValue) value).getComponents();
        } else if (value instanceof FiniteSetValue) {
            parts = ((FiniteSetValue) value).getElements();
        } else {
            return Double.NaN;
        }
        return part < parts.size() ? parts.get(part) : Double.NaN;
    }

    public List<Double> solve() {
        int count = (int) Math.round((SEARCH_END - SEARCH_START) / step) + 1;
        double[] xs = new double[count];
        double[] ds = new double[count];
        boolean[] exactZero = new boolean[count];
        for (int i = 0; i < count; i++) {
            xs[i] = SEARCH_START + i * step;
            ds[i] = difference(xs[i]);
            // 0 from lost value isnt a root
            exactZero[i] = ds[i] == 0 && !context.isAbsorbed();
        }

        undersampled = turnsTooOften(ds);
        List<Double> solutions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (i > 0 && isNumber(ds[i - 1]) && isNumber(ds[i]) && ds[i - 1] * ds[i] < 0) {
                double root = bisect(xs[i - 1], xs[i], ds[i - 1]);
                if (isNumber(root)) {
                    addSolution(solutions, root);
                }
            }
            if (exactZero[i]) {
                boolean zeroBefore = i > 0 && exactZero[i - 1];
                boolean zeroAfter = i < count - 1 && exactZero[i + 1];
                // flat stretch: keep only the ends as hints
                hasStretch = hasStretch || zeroBefore || zeroAfter;
                // same sign both sides, might cross back
                boolean sameSides = i > 0 && i < count - 1 && isNumber(ds[i - 1]) && isNumber(ds[i + 1])
                        && ds[i - 1] * ds[i + 1] > 0;
                if (sameSides) {
                    addHiddenCrossing(solutions, xs[i - 1], ds[i - 1], xs[i]);
                }
                if (!zeroBefore || !zeroAfter) {
                    addSolution(solutions, xs[i]);
                }
                if (sameSides) {
                    addHiddenCrossing(solutions, xs[i + 1], ds[i + 1], xs[i]);
                }
            } else if (isTouchingCandidate(ds, i)) {
                // dip might cross 0 twice or touch
                double sign = Math.signum(ds[i]);
                double[] lowest = lowest(x -> sign * searchDifference(x), xs[i - 1], xs[i + 1]);
                if (isNumber(lowest[1]) && lowest[1] < 0) {
                    double first = bisect(xs[i - 1], lowest[0], ds[i - 1]);
                    double second = bisect(lowest[0], xs[i + 1], difference(lowest[0]));
                    for (double root : new double[] {first, second}) {
                        if (isNumber(root)) {
                            addSolution(solutions, root);
                        }
                    }
                } else {
                    double root = touchingRoot(xs[i - 1], xs[i + 1], Math.abs(ds[i]));
                    if (isNumber(root)) {
                        addSolution(solutions, root);
                    }
                }
            }
        }
        return solutions;
    }

    // true if solve hit an equal stretch, list incomplete
    public boolean hasStretch() {
        return hasStretch;
    }

    // root between outside pt and exact zero
    private void addHiddenCrossing(List<Double> solutions, double outside, double dOutside, double zero) {
        double low = Math.min(outside, zero);
        double high = Math.max(outside, zero);
        double[] lowest = lowest(x -> Math.signum(dOutside) * searchDifference(x), low, high);
        if (!isNumber(lowest[1]) || lowest[1] >= 0) {
            return;
        }
        double root = bisect(Math.min(outside, lowest[0]), Math.max(outside, lowest[0]),
                difference(Math.min(outside, lowest[0])));
        if (isNumber(root)) {
            addSolution(solutions, root);
        }
    }

    // {x, f(x)} min via golden section
    public static double[] lowest(DoubleUnaryOperator f, double low, double high) {
        double a = low;
        double b = high;
        double c = b - GOLDEN * (b - a);
        double d = a + GOLDEN * (b - a);
        double fc = f.applyAsDouble(c);
        double fd = f.applyAsDouble(d);
        for (int round = 0; round < GOLDEN_ROUNDS && b - a > 1e-15 * Math.max(1, Math.abs(a)); round++) {
            if (Double.isNaN(fc) || Double.isNaN(fd)) {
                return new double[] {Double.NaN, Double.NaN};
            }
            if (fc <= fd) {
                b = d;
                d = c;
                fd = fc;
                c = b - GOLDEN * (b - a);
                fc = f.applyAsDouble(c);
            } else {
                a = c;
                c = d;
                fc = fd;
                d = a + GOLDEN * (b - a);
                fd = f.applyAsDouble(d);
            }
        }
        return fc <= fd ? new double[] {c, fc} : new double[] {d, fd};
    }

    // curve turns too much, missed roots
    public boolean isUndersampled() {
        return undersampled;
    }

    private static boolean turnsTooOften(double[] ds) {
        int turns = 0;
        for (int i = 1; i + 1 < ds.length; i++) {
            double before = ds[i] - ds[i - 1];
            double after = ds[i + 1] - ds[i];
            double noise = TURN_NOISE * Math.max(1, Math.abs(ds[i]));
            if (before * after < 0 && Math.abs(before) > noise && Math.abs(after) > noise) {
                turns++;
            }
        }
        return turns > MAX_TURNS_PER_POINT * ds.length;
    }

    // dip w/ no sign change might touch 0, search if deep or curve says so
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
        if (b <= DEEP * Math.min(a, c)) {
            return true;
        }
        double curvature = a + c - 2 * b;
        double parabolaLowest = b - (c - a) * (c - a) / (8 * curvature);
        double cornerLowest = (2 * b + Math.min(a, c) - Math.max(a, c)) / 2;
        return Math.min(parabolaLowest, cornerLowest) < TOUCHING_SHARE * b;
    }

    // golden section for min |diff|, mid of flat bottom
    private double touchingRoot(double low, double high, double atGrid) {
        double a = low;
        double b = high;
        double c = b - GOLDEN * (b - a);
        double d = a + GOLDEN * (b - a);
        double fc = Math.abs(searchDifference(c));
        double fd = Math.abs(searchDifference(d));
        for (int round = 0; round < GOLDEN_ROUNDS && b - a > 1e-15 * Math.max(1, Math.abs(a)); round++) {
            if (!isNumber(fc) || !isNumber(fd)) {
                return Double.NaN;
            }
            if (fc <= fd) {
                b = d;
                d = c;
                fd = fc;
                c = b - GOLDEN * (b - a);
                fc = Math.abs(searchDifference(c));
            } else {
                a = c;
                c = d;
                fc = fd;
                d = a + GOLDEN * (b - a);
                fd = Math.abs(searchDifference(d));
            }
        }
        double best = fc <= fd ? c : d;
        double lowest = Math.min(fc, fd);
        // real touch dips way below grid val, noise doesnt
        if (!isNumber(lowest) || lowest >= ZERO || lowest > DEEP * atGrid) {
            return Double.NaN;
        }
        double left = edgeOfBottom(best, low);
        double right = edgeOfBottom(best, high);
        double root = (left + right) / 2;
        double dRoot = searchDifference(root);
        if (!isNumber(dRoot) || Math.abs(dRoot) >= ZERO || context.isAbsorbed()) {
            return Double.NaN;
        }
        return root;
    }

    // where |diff| goes past FLAT_BOTTOM
    private double edgeOfBottom(double inside, double outside) {
        double dOutside = Math.abs(searchDifference(outside));
        if (!isNumber(dOutside) || dOutside <= FLAT_BOTTOM) {
            return inside;
        }
        for (int round = 0; round < EDGE_ROUNDS; round++) {
            double middle = (inside + outside) / 2;
            double dMiddle = Math.abs(searchDifference(middle));
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

    // dip searches share the budget, too many -> uncalc
    private double searchDifference(double x) {
        context.spendStep();
        return difference(x);
    }

    // ctx says if anything got lost to rounding
    private double difference(double x) {
        context.setAbsorbed(false);
        Context withX = context.withVariable(variable, x);
        try {
            return partOf(left.evaluate(withX), leftPart) - partOf(right.evaluate(withX), rightPart);
        } catch (UndefinedException e) {
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
