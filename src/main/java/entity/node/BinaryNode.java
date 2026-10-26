package entity.node;

import entity.Context;
import entity.UncalculableException;
import entity.value.BooleanValue;
import entity.value.NumberValue;
import entity.value.Value;
import entity.value.Values;

import java.util.Arrays;
import java.util.List;

public class BinaryNode extends Node {
    public enum Op { ADD, SUBTRACT, MULTIPLY, DOT, CROSS, DIVIDE, POWER, AND, OR, XOR, IMPLIES, IFF }

    private final Op op;
    private final Node left;
    private final Node right;

    public BinaryNode(Op op, Node left, Node right) {
        this.op = op;
        this.left = left;
        this.right = right;
    }

    public Op getOp() {
        return op;
    }

    public Node getLeft() {
        return left;
    }

    public Node getRight() {
        return right;
    }

    @Override
    public List<Node> children() {
        return Arrays.asList(left, right);
    }

    @Override
    public boolean isCondition() {
        return isLogic();
    }

    private boolean isLogic() {
        return op == Op.AND || op == Op.OR || op == Op.XOR || op == Op.IMPLIES || op == Op.IFF;
    }

    @Override
    public Value evaluate(Context context) {
        if (isLogic()) {
            boolean p = Values.toBoolean(left.evaluate(context));
            boolean q = Values.toBoolean(right.evaluate(context));
            return BooleanValue.of(logic(p, q));
        }
        Value a = left.evaluate(context);
        Value b = right.evaluate(context);
        return new NumberValue(calculate(Values.toNumber(a), Values.toNumber(b)));
    }

    private boolean logic(boolean p, boolean q) {
        switch (op) {
            case AND:
                return p && q;
            case OR:
                return p || q;
            case XOR:
                return p != q;
            case IMPLIES:
                return !p || q;
            default:
                return p == q;
        }
    }

    private double calculate(double a, double b) {
        switch (op) {
            case ADD:
                return a + b;
            case SUBTRACT:
                return a - b;
            case MULTIPLY:
            case DOT:
            case CROSS:
                return a * b;
            case DIVIDE:
                if (b == 0) {
                    return Double.NaN;
                }
                return a / b;
            case POWER:
                if (a == 0 && b < 0) {
                    return Double.NaN;
                }
                return Math.pow(a, b);
            default:
                throw new UncalculableException();
        }
    }
}
