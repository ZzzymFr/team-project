package entity.node;

import entity.Context;
import entity.UncalculableException;
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
        boolean[] lost = {false};
        double limit = LimitCalculator.limit(t -> sample(context.withVariable(variable, t), lost),
                t -> NoiseScale.noiseOf(body, context.withVariable(variable, t)), point, side);
        if (Double.isNaN(limit) && lost[0]) {
            // inf-inf in the samples, not a real "no limit"
            throw new UncalculableException(UncalculableException.TOO_BIG);
        }
        return new NumberValue(limit);
    }

    private double sample(Context context, boolean[] lost) {
        boolean outer = context.isAbsorbed();
        context.setAbsorbed(false);
        try {
            double value = DerivativeNode.numberAt(body, context);
            if (Double.isNaN(value) && context.isAbsorbed()) {
                lost[0] = true;
            }
            return value;
        } catch (UndefinedException e) {
            return Double.NaN;
        } finally {
            context.setAbsorbed(outer || context.isAbsorbed());
        }
    }
}
