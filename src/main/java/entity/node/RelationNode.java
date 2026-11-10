package entity.node;

import entity.Context;
import entity.OverflowException;
import entity.UncalculableException;
import entity.UncalculableReason;
import entity.UndefinedException;
import entity.value.BooleanValue;
import entity.value.VectorValue;
import entity.value.NumberValue;
import entity.value.Value;
import entity.value.Values;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RelationNode extends Node {
    private final List<Node> operands;
    private final List<String> operators;
    private boolean[] plain;

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
        double outer = context.getApproximation();
        context.setApproximation(0);
        Value left = comparable(operands.get(0), context);
        double leftError = context.getApproximation();
        boolean result = true;
        for (int i = 0; i < operators.size(); i++) {
            context.setApproximation(0);
            Value right = comparable(operands.get(i + 1), context);
            double rightError = context.getApproximation();
            String operator = operators.get(i);
            boolean approximate = operands.get(i).isApproximate(context) || operands.get(i + 1).isApproximate(context);
            double error = approximate ? Math.max(leftError, rightError) : -1;
            double exact = exactTolerance(i, left, right, context);
            boolean tolerated = Double.isNaN(exact) ? isDecidedByTolerance(operator, left, right, error)
                    : isDecidedWithin(operator, left, right, exact);
            if (result && tolerated) {
                context.markTolerated();
            }
            result = result && holds(operator, left, right, operands.get(i), operands.get(i + 1), exact);
            left = right;
            leftError = rightError;
            outer = Math.max(outer, rightError);
        }
        context.setApproximation(Math.max(outer, leftError));
        return BooleanValue.of(result);
    }

    // plain constants on both sides: tol from their own float noise, NaN = usual tol
    private double exactTolerance(int index, Value left, Value right, Context context) {
        if (plain == null) {
            plain = new boolean[operands.size()];
            for (int i = 0; i < operands.size(); i++) {
                plain[i] = NoiseScale.isSupported(operands.get(i));
            }
        }
        if (!plain[index] || !plain[index + 1] || !(left instanceof NumberValue)
                || !(right instanceof NumberValue)) {
            return Double.NaN;
        }
        double scale = NoiseScale.of(operands.get(index), context) + NoiseScale.of(operands.get(index + 1), context);
        return NoiseTolerance.of(scale, ((NumberValue) left).getValue(), ((NumberValue) right).getValue());
    }

    // like isDecidedByTolerance but w/ the noise tol
    private static boolean isDecidedWithin(String operator, Value left, Value right, double exact) {
        double a = ((NumberValue) left).getValue();
        double b = ((NumberValue) right).getValue();
        if (a == b || Math.abs(a - b) > exact) {
            return false;
        }
        switch (operator) {
            case "=":
            case "\\neq":
                return true;
            case "<":
            case "\\geq":
                return a < b;
            case ">":
            case "\\leq":
                return a > b;
            default:
                return false;
        }
    }

    // undefined or overflowed side cant compare
    private static Value comparable(Node operand, Context context) {
        Value value = operand.evaluate(context);
        if (value instanceof NumberValue) {
            double number = ((NumberValue) value).getValue();
            if (Double.isNaN(number)) {
                throw new UndefinedException();
            }
            if (Double.isInfinite(number) && !operand.canBeInfinite(context)) {
                throw new OverflowException();
            }
        }
        return value;
    }

    // within tol or within approx error
    private static boolean isDecidedByTolerance(String operator, Value left, Value right, double error) {
        if (!(left instanceof NumberValue) || !(right instanceof NumberValue)) {
            return false;
        }
        double a = ((NumberValue) left).getValue();
        double b = ((NumberValue) right).getValue();
        if (a == b) {
            return false;
        }
        if (error >= 0 && Values.areApproximatelyEqual(a, b, error) && !Values.areAlmostEqual(a, b)) {
            return operator.equals("=") || operator.equals("\\neq") || operator.equals("<")
                    || operator.equals(">") || operator.equals("\\leq") || operator.equals("\\geq");
        }
        if (!Values.areAlmostEqual(a, b)) {
            return false;
        }
        switch (operator) {
            case "=":
            case "\\neq":
                return true;
            case "<":
            case "\\geq":
                return a < b;
            case ">":
            case "\\leq":
                return a > b;
            default:
                return false;
        }
    }

    private boolean holds(String operator, Value left, Value right, Node leftNode, Node rightNode, double exact) {
        switch (operator) {
            case "=":
                return areEqual(left, right, exact);
            case "\\neq":
                return !areEqual(left, right, exact);
            case "<":
                return Values.toNumber(left) < Values.toNumber(right) && !almostEqual(left, right, exact);
            case ">":
                return Values.toNumber(left) > Values.toNumber(right) && !almostEqual(left, right, exact);
            case "\\leq":
                return Values.toNumber(left) < Values.toNumber(right) || almostEqual(left, right, exact);
            case "\\geq":
                return Values.toNumber(left) > Values.toNumber(right) || almostEqual(left, right, exact);
            case "\\approx":
                return approximately(Values.toNumber(left), Values.toNumber(right), leftNode, rightNode);
            case "\\perp":
                return isPerpendicular(left, right);
            case "\\in":
                return Membership.isMember(left, right, leftNode);
            case "\\notin":
                return !Membership.isMember(left, right, leftNode);
            case "\\subset":
                return Membership.isSubset(left, right);
            default:
                throw new UncalculableException();
        }
    }

    private boolean isPerpendicular(Value left, Value right) {
        if (!(left instanceof VectorValue) || !(right instanceof VectorValue)) {
            throw new UncalculableException(UncalculableReason.WRONG_KIND);
        }
        double product = BinaryNode.dot(((VectorValue) left).getComponents(),
                ((VectorValue) right).getComponents());
        return Values.areAlmostEqual(product, 0);
    }

    private boolean areEqual(Value left, Value right, double exact) {
        if (Double.isNaN(exact)) {
            return Values.areEqual(left, right);
        }
        return almostEqual(left, right, exact);
    }

    private boolean almostEqual(Value left, Value right, double exact) {
        return NoiseTolerance.within(Values.toNumber(left), Values.toNumber(right), exact);
    }

    private boolean approximately(double a, double b, Node leftNode, Node rightNode) {
        if (Double.isNaN(a) || Double.isNaN(b)) {
            return false;
        }
        if (Double.isInfinite(a) || Double.isInfinite(b)) {
            return a == b;
        }
        if (SignedNumber.is(rightNode)) {
            return withinRounding(a, b, SignedNumber.decimalsOf(rightNode));
        }
        if (SignedNumber.is(leftNode)) {
            return withinRounding(a, b, SignedNumber.decimalsOf(leftNode));
        }
        return Math.abs(a - b) <= 0.01 * Math.max(Math.abs(a), Math.abs(b));
    }

    private boolean withinRounding(double a, double b, int decimals) {
        return Math.abs(a - b) <= 0.5 * Math.pow(10, -decimals) + 1e-12;
    }

    @Override
    public <R> R accept(NodeVisitor<R> visitor) {
        return visitor.visitRelation(this);
    }
}
