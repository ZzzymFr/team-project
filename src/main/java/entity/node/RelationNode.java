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
            result = result && holds(operators.get(i), left, right);
            left = right;
        }
        return BooleanValue.of(result);
    }

    private boolean holds(String operator, Value left, Value right) {
        if (operator.equals("=")) {
            return Values.areEqual(left, right);
        }
        throw new UncalculableException();
    }
}
