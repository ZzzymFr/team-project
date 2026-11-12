package entity;

import entity.node.BinaryNode;
import entity.node.FunctionNode;
import entity.node.Node;
import entity.node.RelationNode;
import entity.node.UnaryNode;
import entity.value.NumberValue;
import entity.value.Value;
import entity.value.Values;

import java.util.List;

// is a sample real or just float noise
final class RoundingCheck {
    static final int RELIABLE = 0;
    private static final int NOISY = 1;
    static final int OUT_OF_RANGE = 2;
    private static final double HUGE = 1e300;
    private static final double NOISE = 1e-12;

    private final String variable;
    private final Context context;
    private final ComparisonFinder comparisons;

    RoundingCheck(String variable, Context context, ComparisonFinder comparisons) {
        this.variable = variable;
        this.context = context;
        this.comparisons = comparisons;
    }

    // skip samples that only show rounding
    int reliabilityAt(double point, boolean atRoot) {
        boolean far = Math.abs(point) > EquationSolver.SEARCH_END;
        Context at = context.withVariable(variable, point);
        for (RelationNode relation : comparisons.relations()) {
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
                        if (far && !(size <= HUGE && (size >= SideScan.TINY || atRoot))) {
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
                if (ComparisonFinder.isSetOperator(relation.getOperators().get(i))
                        || !Double.isFinite(a) || !Double.isFinite(b)) {
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
                boolean approximate = operands.get(i).isApproximate(at) || operands.get(i + 1).isApproximate(at);
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

    // lost value only matters if sides are that close
    boolean isRoundingOnly(Node node, Context at) {
        if (!(node instanceof RelationNode)) {
            return true;
        }
        RelationNode relation = (RelationNode) node;
        List<Node> operands = relation.getOperands();
        for (int i = 0; i + 1 < operands.size(); i++) {
            if (ComparisonFinder.isSetOperator(relation.getOperators().get(i))) {
                continue;
            }
            double a = numberAt(operands.get(i), at);
            double b = numberAt(operands.get(i + 1), at);
            if (!Double.isFinite(a) || !Double.isFinite(b)) {
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

    // max size in the arithmetic, bounds rounding err
    private static double largestPart(Node node, Context at) {
        double largest = 0;
        try {
            Value value = node.evaluate(at);
            if (value instanceof NumberValue && Double.isFinite(((NumberValue) value).getValue())) {
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

    private double outerScale() {
        double scale = 0;
        for (double value : context.getVariableValues()) {
            scale = Math.max(scale, Math.abs(value));
        }
        return scale;
    }
}
