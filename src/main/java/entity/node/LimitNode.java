package entity.node;

import entity.Context;
import entity.UndefinedException;
import entity.calculus.LimitCalculator;
import entity.value.NumberValue;
import entity.value.Value;
import entity.value.Values;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LimitNode extends Node {
    private final String variable;
    private final Node target;
    private final int side;
    private final Node body;

    public LimitNode(String variable, Node target, int side, Node body) {
        this.variable = variable;
        this.target = target;
        this.side = side;
        this.body = body;
    }

    public String getVariable() {
        return variable;
    }

    public Node getTarget() {
        return target;
    }

    public int getSide() {
        return side;
    }

    public Node getBody() {
        return body;
    }

    @Override
    public List<Node> children() {
        return Arrays.asList(target, body);
    }

    @Override
    public void collectFreeVariables(Set<String> names) {
        Set<String> inBody = new HashSet<>();
        body.collectFreeVariables(inBody);
        inBody.remove(variable);
        names.addAll(inBody);
        target.collectFreeVariables(names);
    }

    @Override
    public boolean canBeInfinite() {
        return true;
    }

    @Override
    public boolean isApproximate() {
        return true;
    }

    @Override
    public Value evaluate(Context context) {
        double point = Values.toNumber(target.evaluate(context));
        return new NumberValue(LimitCalculator.limit(t -> sample(context.withVariable(variable, t)), point, side));
    }

    private double sample(Context context) {
        try {
            return DerivativeNode.numberAt(body, context);
        } catch (UndefinedException e) {
            return Double.NaN;
        }
    }
}
