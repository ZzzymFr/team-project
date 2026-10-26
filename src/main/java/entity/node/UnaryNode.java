package entity.node;

import entity.Context;
import entity.UncalculableException;
import entity.value.NumberValue;
import entity.value.Value;
import entity.value.Values;

import java.util.Collections;
import java.util.List;

public class UnaryNode extends Node {
    public enum Op { NEGATE, FACTORIAL, ABSOLUTE }

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
    public Value evaluate(Context context) {
        double value = Values.toNumber(operand.evaluate(context));
        switch (op) {
            case NEGATE:
                return new NumberValue(-value);
            case FACTORIAL:
                return new NumberValue(factorial(value));
            case ABSOLUTE:
                return new NumberValue(Math.abs(value));
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
