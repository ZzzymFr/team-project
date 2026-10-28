package entity.node;

import entity.Context;
import entity.calculus.DerivativeCalculator;
import entity.value.NumberValue;
import entity.value.Value;

import java.util.Collections;
import java.util.List;
import java.util.Set;

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
    public void collectFreeVariables(Set<String> names) {
        body.collectFreeVariables(names);
        names.add(variable);
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
        double[] result = DerivativeCalculator.derivativeWithError(
                t -> numberAt(body, context.withVariable(variable, t)), x, order);
        context.noteApproximation(result[1]);
        return new NumberValue(result[0]);
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
