package entity.node;

import entity.Context;
import entity.UncalculableException;
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
        Value left = defined(operands.get(0).evaluate(context));
        boolean result = true;
        for (int i = 0; i < operators.size(); i++) {
            Value right = defined(operands.get(i + 1).evaluate(context));
            String operator = operators.get(i);
            if (result && isDecidedByTolerance(operator, left, right)) {
                context.markTolerated();
            }
            result = result && holds(operator, left, right, operands.get(i), operands.get(i + 1));
            left = right;
        }
        return BooleanValue.of(result);
    }

    // undefined side cant compare
    private static Value defined(Value value) {
        if (value instanceof NumberValue && Double.isNaN(((NumberValue) value).getValue())) {
            throw new UncalculableException();
        }
        return value;
    }

    // unequal but within tol
    private static boolean isDecidedByTolerance(String operator, Value left, Value right) {
        if (!(left instanceof NumberValue) || !(right instanceof NumberValue)) {
            return false;
        }
        double a = ((NumberValue) left).getValue();
        double b = ((NumberValue) right).getValue();
        if (a == b || !Values.areAlmostEqual(a, b)) {
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
            throw new UncalculableException();
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
