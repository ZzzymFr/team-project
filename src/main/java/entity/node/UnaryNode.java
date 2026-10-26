package entity.node;

import entity.Context;
import entity.UncalculableException;
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
            return BooleanValue.of(!Values.toBoolean(operand.evaluate(context)));
        }
        Value operandValue = operand.evaluate(context);
        if (op == Op.ABSOLUTE && operandValue instanceof FiniteSetValue) {
            return new NumberValue(((FiniteSetValue) operandValue).getElements().size());
        }
        if (operandValue instanceof VectorValue) {
            return vectorOperate((VectorValue) operandValue);
        }
        if (op == Op.UNIT) {
            throw new UncalculableException();
        }
        double value = Values.toNumber(operandValue);
        switch (op) {
            case NEGATE:
                return new NumberValue(-value);
            case FACTORIAL:
                return new NumberValue(factorial(value));
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
                if (norm == 0 || Double.isInfinite(norm)) {
                    throw new UncalculableException();
                }
                for (double component : vector.getComponents()) {
                    scaled.add(component / norm);
                }
                return BinaryNode.finiteVector(scaled);
            default:
                throw new UncalculableException();
        }
    }

    private static double factorial(double n) {
        if (n < 0 || n != Math.floor(n)) {
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
}
