package entity.node;

import entity.Context;
import entity.OverflowException;
import entity.UncalculableException;
import entity.UndefinedException;
import entity.value.BooleanValue;
import entity.value.FiniteSetValue;
import entity.value.NumberSetValue;
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
            boolean approximate = operands.get(i).isApproximate() || operands.get(i + 1).isApproximate();
            double error = approximate ? Math.max(leftError, rightError) : -1;
            if (result && isDecidedByTolerance(operator, left, right, error)) {
                context.markTolerated();
            }
            result = result && holds(operator, left, right, operands.get(i), operands.get(i + 1));
            left = right;
            leftError = rightError;
            outer = Math.max(outer, rightError);
        }
        context.setApproximation(Math.max(outer, leftError));
        return BooleanValue.of(result);
    }

    // undefined or overflowed side cant compare
    private static Value comparable(Node operand, Context context) {
        Value value = operand.evaluate(context);
        if (value instanceof NumberValue) {
            double number = ((NumberValue) value).getValue();
            if (Double.isNaN(number)) {
                throw new UndefinedException();
            }
            if (Double.isInfinite(number) && !operand.canBeInfinite()) {
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
            case "\\perp":
                return isPerpendicular(left, right);
            case "\\in":
                return isMember(left, right, leftNode);
            case "\\notin":
                return !isMember(left, right, leftNode);
            case "\\subset":
                return isSubset(left, right);
            default:
                throw new UncalculableException();
        }
    }

    private boolean isPerpendicular(Value left, Value right) {
        if (!(left instanceof VectorValue) || !(right instanceof VectorValue)) {
            throw new UncalculableException();
        }
        double product = BinaryNode.dot(((VectorValue) left).getComponents(),
                ((VectorValue) right).getComponents());
        return Values.areAlmostEqual(product, 0);
    }

    private boolean isMember(Value element, Value set, Node elementNode) {
        if (element instanceof NumberValue && Double.isNaN(((NumberValue) element).getValue())) {
            throw new UndefinedException();
        }
        if (element instanceof NumberValue && set instanceof FiniteSetValue) {
            return ((FiniteSetValue) set).contains(((NumberValue) element).getValue());
        }
        if (set instanceof NumberSetValue) {
            NumberSetValue numbers = (NumberSetValue) set;
            if (element instanceof VectorValue) {
                return numbers.containsVector((VectorValue) element);
            }
            double number = Values.toNumber(element);
            if (numbers.getKind() == NumberSetValue.Kind.RATIONAL && numbers.getDimension() == 1
                    && !Double.isNaN(number) && !Double.isInfinite(number)
                    && number != Math.floor(number)) {
                if (isLiteral(elementNode)) {
                    return true;
                }
                throw new UncalculableException();
            }
            return numbers.containsNumber(number);
        }
        throw new UncalculableException();
    }

    private boolean isLiteral(Node node) {
        if (node instanceof UnaryNode && ((UnaryNode) node).getOp() == UnaryNode.Op.NEGATE) {
            return isLiteral(((UnaryNode) node).getOperand());
        }
        return node instanceof NumberNode;
    }

    private boolean isSubset(Value left, Value right) {
        if (left instanceof FiniteSetValue && right instanceof FiniteSetValue) {
            return ((FiniteSetValue) left).isSubsetOf((FiniteSetValue) right);
        }
        if (left instanceof FiniteSetValue && right instanceof NumberSetValue) {
            for (double element : ((FiniteSetValue) left).getElements()) {
                if (!((NumberSetValue) right).containsNumber(element)) {
                    return false;
                }
            }
            return true;
        }
        if (left instanceof NumberSetValue && right instanceof NumberSetValue) {
            return ((NumberSetValue) left).isSubsetOf((NumberSetValue) right);
        }
        if (left instanceof NumberSetValue && right instanceof FiniteSetValue) {
            return false;
        }
        throw new UncalculableException();
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
        if (isLiteral(rightNode)) {
            return withinRounding(a, b, decimalsOf(rightNode));
        }
        if (isLiteral(leftNode)) {
            return withinRounding(a, b, decimalsOf(leftNode));
        }
        return Math.abs(a - b) <= 0.01 * Math.max(Math.abs(a), Math.abs(b));
    }

    // decimals in literal, incl -3.14
    private static int decimalsOf(Node literal) {
        if (literal instanceof UnaryNode) {
            return decimalsOf(((UnaryNode) literal).getOperand());
        }
        return ((NumberNode) literal).getDecimals();
    }

    private boolean withinRounding(double a, double b, int decimals) {
        return Math.abs(a - b) <= 0.5 * Math.pow(10, -decimals) + 1e-12;
    }
}
