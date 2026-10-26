package entity.node;

import entity.Context;
import entity.UncalculableException;
import entity.value.BooleanValue;
import entity.value.Value;
import entity.value.Values;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RelationNode extends Node {
    private final List<Node> operands;
    private final List<String> operators;

    public RelationNode(List<Node> operands, List<String> operators) {
        this.operands = Collections.unmodifiableList(new ArrayList<>(operands));
        this.operators = Collections.unmodifiableList(new ArrayList<>(operators));
    }

    public List<Node> getOperands() {
        return operands;
    }

    public List<String> getOperators() {
        return operators;
    }

    @Override
    public List<Node> children() {
        return operands;
    }

    @Override
    public boolean isCondition() {
        return true;
    }

    @Override
    public Value evaluate(Context context) {
        Value left = operands.get(0).evaluate(context);
        boolean result = true;
        for (int i = 0; i < operators.size(); i++) {
            Value right = operands.get(i + 1).evaluate(context);
            result = result && holds(operators.get(i), left, right, operands.get(i), operands.get(i + 1));
            left = right;
        }
        return BooleanValue.of(result);
    }

    private boolean holds(String operator, Value left, Value right, Node leftNode, Node rightNode) {
        switch (operator) {
            case "=":
                return Values.areEqual(left, right);
            case "\\neq":
                return !Values.areEqual(left, right);
            case "<":
                return Values.toNumber(left) < Values.toNumber(right) && !almostEqual(left, right);
            case ">":
                return Values.toNumber(left) > Values.toNumber(right) && !almostEqual(left, right);
            case "\\leq":
                return Values.toNumber(left) < Values.toNumber(right) || almostEqual(left, right);
            case "\\geq":
                return Values.toNumber(left) > Values.toNumber(right) || almostEqual(left, right);
            case "\\approx":
                return approximately(Values.toNumber(left), Values.toNumber(right), leftNode, rightNode);
            default:
                throw new UncalculableException();
        }
    }

    private boolean almostEqual(Value left, Value right) {
        return Values.areAlmostEqual(Values.toNumber(left), Values.toNumber(right));
    }

    private boolean approximately(double a, double b, Node leftNode, Node rightNode) {
        if (Double.isNaN(a) || Double.isNaN(b)) {
            return false;
        }
        if (Double.isInfinite(a) || Double.isInfinite(b)) {
            return a == b;
        }
        if (rightNode instanceof NumberNode) {
            return withinRounding(a, b, ((NumberNode) rightNode).getDecimals());
        }
        if (leftNode instanceof NumberNode) {
            return withinRounding(a, b, ((NumberNode) leftNode).getDecimals());
        }
        return Math.abs(a - b) <= 0.01 * Math.max(Math.abs(a), Math.abs(b));
    }

    private boolean withinRounding(double a, double b, int decimals) {
        return Math.abs(a - b) <= 0.5 * Math.pow(10, -decimals) + 1e-12;
    }
}
