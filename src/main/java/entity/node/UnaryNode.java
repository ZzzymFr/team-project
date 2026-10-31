package entity.node;

import entity.Context;
import entity.UncalculableException;
import entity.UndefinedException;
import entity.value.BooleanValue;
import entity.value.FiniteSetValue;
import entity.value.NumberValue;
import entity.value.Value;
import entity.value.Values;
import entity.value.VectorValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UnaryNode extends Node {
    public enum Op { NEGATE, FACTORIAL, ABSOLUTE, PERCENT, NOT, NORM, UNIT }

    private static final double LANCZOS_G = 7;
    private static final double[] LANCZOS = {0.99999999999980993, 676.5203681218851, -1259.1392167224028,
        771.32342877765313, -176.61502916214059, 12.507343278686905, -0.13857109526572012,
        9.9843695780195716e-6, 1.5056327351493116e-7};

    private final Op op;
    private final Node operand;

    public UnaryNode(Op op, Node operand) {
        this.op = op;
        this.operand = operand;
    }

    public Op getOp() {
        return op;
    }

    public Node getOperand() {
        return operand;
    }

    @Override
    public List<Node> children() {
        return Collections.singletonList(operand);
    }

    @Override
    public boolean isCondition() {
        return op == Op.NOT;
    }

    @Override
    public Value evaluate(Context context) {
        if (op == Op.NOT) {
            return BooleanValue.of(!BinaryNode.truth(operand.evaluate(context)));
        }
        Value operandValue = operand.evaluate(context);
        if (op == Op.ABSOLUTE && operandValue instanceof FiniteSetValue) {
            return new NumberValue(((FiniteSetValue) operandValue).getElements().size());
        }
        if (operandValue instanceof VectorValue) {
            return vectorOperate((VectorValue) operandValue);
        }
        if (op == Op.UNIT) {
            throw new UncalculableException(UncalculableException.WRONG_KIND);
        }
        double value = Values.toNumber(operandValue);
        switch (op) {
            case NEGATE:
                return new NumberValue(-value);
            case FACTORIAL:
                double result = factorial(value);
                if (Double.isInfinite(result) && !Double.isInfinite(value)) {
                    // overflow = lost value, like 10^{400}
                    context.markAbsorbed();
                }
                return new NumberValue(result);
            case ABSOLUTE:
            case NORM:
                return new NumberValue(Math.abs(value));
            case PERCENT:
                return new NumberValue(value / 100);
            default:
                throw new UncalculableException();
        }
    }

    private Value vectorOperate(VectorValue vector) {
        double norm = BinaryNode.dot(vector.getComponents(), vector.getComponents());
        norm = Math.sqrt(norm);
        List<Double> scaled = new ArrayList<>();
        switch (op) {
            case ABSOLUTE:
            case NORM:
                return new NumberValue(norm);
            case NEGATE:
                for (double component : vector.getComponents()) {
                    scaled.add(-component);
                }
                return BinaryNode.finiteVector(scaled);
            case UNIT:
                if (norm == 0) {
                    throw new UndefinedException(UncalculableException.ZERO_VECTOR);
                }
                if (Double.isInfinite(norm)) {
                    throw new UncalculableException(UncalculableException.TOO_BIG);
                }
                for (double component : vector.getComponents()) {
                    scaled.add(component / norm);
                }
                return BinaryNode.finiteVector(scaled);
            default:
                throw new UncalculableException();
        }
    }

    // whole n: exact product; n > -1: gamma(n+1), so 2.5! works; else undefined
    private static double factorial(double n) {
        if (Double.isNaN(n) || n == Double.NEGATIVE_INFINITY) {
            return Double.NaN;
        }
        if (n == Math.floor(n)) {
            if (n < 0) {
                return Double.NaN;
            }
            if (n > 170) {
                return Double.POSITIVE_INFINITY;
            }
            double result = 1;
            for (int i = 2; i <= n; i++) {
                result = result * i;
            }
            return result;
        }
        if (n <= -1) {
            // poles at every neg int, keep x!=24 from getting 100 roots
            return Double.NaN;
        }
        return gamma(n + 1);
    }

    // lanczos g=7, x >= 0 here
    private static double gamma(double x) {
        if (x < 0.5) {
            return Math.PI / (Math.sin(Math.PI * x) * gamma(1 - x));
        }
        double shifted = x - 1;
        double sum = LANCZOS[0];
        for (int i = 1; i < LANCZOS.length; i++) {
            sum = sum + LANCZOS[i] / (shifted + i);
        }
        double t = shifted + LANCZOS_G + 0.5;
        // exp of the log so big x only overflows when the answer does
        return Math.sqrt(2 * Math.PI) * sum * Math.exp((shifted + 0.5) * Math.log(t) - t);
    }
}
