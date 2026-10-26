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
import java.util.Arrays;
import java.util.List;

public class BinaryNode extends Node {
    public enum Op { ADD, SUBTRACT, MULTIPLY, DOT, CROSS, DIVIDE, POWER, AND, OR, XOR, IMPLIES, IFF, UNION, INTERSECT }

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
        if (op == Op.UNION || op == Op.INTERSECT) {
            if (!(a instanceof FiniteSetValue) || !(b instanceof FiniteSetValue)) {
                throw new UncalculableException();
            }
            if (op == Op.UNION) {
                return ((FiniteSetValue) a).union((FiniteSetValue) b);
            }
            return ((FiniteSetValue) a).intersect((FiniteSetValue) b);
        }
        if (a instanceof VectorValue || b instanceof VectorValue) {
            return vectorCalculate(a, b);
        }
        return new NumberValue(calculate(Values.toNumber(a), Values.toNumber(b)));
    }

    private Value vectorCalculate(Value a, Value b) {
        if (a instanceof VectorValue && b instanceof VectorValue) {
            List<Double> x = ((VectorValue) a).getComponents();
            List<Double> y = ((VectorValue) b).getComponents();
            switch (op) {
                case ADD:
                case SUBTRACT:
                    return combine(x, y);
                case MULTIPLY:
                case DOT:
                    return new NumberValue(dot(x, y));
                case CROSS:
                    return cross(x, y);
                default:
                    throw new UncalculableException();
            }
        }
        boolean scalarOnRight = a instanceof VectorValue;
        if (op == Op.DIVIDE && scalarOnRight) {
            double divisor = Values.toNumber(b);
            if (divisor == 0 || Double.isNaN(divisor) || Double.isInfinite(divisor)) {
                throw new UncalculableException();
            }
            List<Double> divided = new ArrayList<>();
            for (double component : ((VectorValue) a).getComponents()) {
                divided.add(component / divisor);
            }
            return finiteVector(divided);
        }
        if (op != Op.MULTIPLY && op != Op.DOT && op != Op.CROSS) {
            throw new UncalculableException();
        }
        double factor;
        List<Double> components;
        if (scalarOnRight) {
            components = ((VectorValue) a).getComponents();
            factor = Values.toNumber(b);
        } else {
            components = ((VectorValue) b).getComponents();
            factor = Values.toNumber(a);
        }
        List<Double> scaled = new ArrayList<>();
        for (double component : components) {
            scaled.add(component * factor);
        }
        return finiteVector(scaled);
    }

    public static VectorValue finiteVector(List<Double> components) {
        for (double component : components) {
            if (Double.isNaN(component) || Double.isInfinite(component)) {
                throw new UncalculableException();
            }
        }
        return new VectorValue(components);
    }

    private Value combine(List<Double> x, List<Double> y) {
        if (x.size() != y.size()) {
            throw new UncalculableException();
        }
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < x.size(); i++) {
            result.add(op == Op.ADD ? x.get(i) + y.get(i) : x.get(i) - y.get(i));
        }
        return finiteVector(result);
    }

    public static double dot(List<Double> x, List<Double> y) {
        if (x.size() != y.size()) {
            throw new UncalculableException();
        }
        double sum = 0;
        for (int i = 0; i < x.size(); i++) {
            sum += x.get(i) * y.get(i);
        }
        return sum;
    }

    private Value cross(List<Double> x, List<Double> y) {
        if (x.size() != 3 || y.size() != 3) {
            throw new UncalculableException();
        }
        return finiteVector(Arrays.asList(
                x.get(1) * y.get(2) - x.get(2) * y.get(1),
                x.get(2) * y.get(0) - x.get(0) * y.get(2),
                x.get(0) * y.get(1) - x.get(1) * y.get(0)));
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
