package entity.node;

import entity.Context;
import entity.value.NumberValue;
import entity.value.Value;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DerivativeNode extends Node {
    private final String variable;
    private final int order;
    private final Node body;
    private final boolean partial;

    public DerivativeNode(String variable, int order, Node body) {
        this(variable, order, body, false);
    }

    public DerivativeNode(String variable, int order, Node body, boolean partial) {
        this.variable = variable;
        this.order = order;
        this.body = body;
        this.partial = partial;
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

    public boolean isPartial() {
        return partial;
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
    public boolean canBeInfinite(Context context) {
        return false;
    }

    @Override
    public boolean isApproximate(Context context) {
        return !context.getDerivative().isExact(body, variable, order, context) || super.isApproximate(context);
    }

    @Override
    public Value evaluate(Context context) {
        double[] result = context.getDerivative().at(body, variable, order, context);
        context.noteApproximation(result[1]);
        return new NumberValue(result[0]);
    }

    public static double numberAt(Node node, Context context) {
        context.spendStep();
        Value value = node.evaluate(context);
        if (value instanceof NumberValue) {
            return ((NumberValue) value).getValue();
        }
        return Double.NaN;
    }

    @Override
    public <R> R accept(NodeVisitor<R> visitor) {
        return visitor.visitDerivative(this);
    }

    @Override
    public boolean containsDerivative() {
        return true;
    }
}
