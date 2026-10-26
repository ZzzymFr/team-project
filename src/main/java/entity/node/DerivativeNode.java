package entity.node;

import entity.Context;
import entity.calculus.DerivativeCalculator;
import entity.value.NumberValue;
import entity.value.Value;

import java.util.Collections;
import java.util.List;

public class DerivativeNode extends Node {
    private final String variable;
    private final int order;
    private final Node body;

    public DerivativeNode(String variable, int order, Node body) {
        this.variable = variable;
        this.order = order;
        this.body = body;
    }

    public String getVariable() {
        return variable;
    }

    public int getOrder() {
        return order;
    }

    public Node getBody() {
        return body;
    }

    @Override
    public List<Node> children() {
        return Collections.singletonList(body);
    }

    @Override
    public boolean canBeInfinite() {
        return false;
    }

    @Override
    public boolean isApproximate() {
        return true;
    }

    @Override
    public Value evaluate(Context context) {
        double x = context.getVariable(variable);
        return new NumberValue(DerivativeCalculator.derivative(
                t -> numberAt(body, context.withVariable(variable, t)), x, order));
    }

    static double numberAt(Node node, Context context) {
        context.spendStep();
        Value value = node.evaluate(context);
        if (value instanceof NumberValue) {
            return ((NumberValue) value).getValue();
        }
        return Double.NaN;
    }
}
