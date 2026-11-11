package entity.node;

import entity.Context;
import entity.UndefinedException;
import entity.calculus.LimitCalculator;
import entity.value.NumberValue;
import entity.value.Value;
import entity.value.Values;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class LimitNode extends Node {
    private final String variable;
    private final Node target;
    private final int side;
    private final Node body;
    // answer only hangs on these, solver asks the same x many times
    private final List<String> free;

    public LimitNode(String variable, Node target, int side, Node body) {
        this.variable = variable;
        this.target = target;
        this.side = side;
        this.body = body;
        Set<String> names = new TreeSet<>();
        collectFreeVariables(names);
        this.free = new ArrayList<>(names);
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
    public boolean canBeInfinite(Context context) {
        return true;
    }

    @Override
    public boolean isApproximate(Context context) {
        return true;
    }

    @Override
    public Value evaluate(Context context) {
        return context.once(this, free, () -> limitOf(context));
    }

    private Value limitOf(Context context) {
        double point = Values.toNumber(target.evaluate(context));
        Losses losses = new Losses();
        double limit = LimitCalculator.limit(t -> sample(t, context, losses),
                t -> noiseAt(t, context),
                t -> t == losses.absorbedAt, point, side);
        return new NumberValue(limit);
    }

    private double sample(double t, Context outerContext, Losses losses) {
        Context context = outerContext.withVariable(variable, t);
        boolean outer = context.isAbsorbed();
        context.setAbsorbed(false);
        losses.absorbedAt = Double.NaN;
        try {
            double value = DerivativeNode.numberAt(body, context);
            if (context.isAbsorbed()) {
                losses.absorbedAt = t;
            }
            return value;
        } catch (UndefinedException e) {
            return Double.NaN;
        } finally {
            // calculator already gets this per sample, dont leak it to the outer solver
            context.setAbsorbed(outer);
        }
    }

    // only a size estimate, rounding inside it isnt the answer's
    private double noiseAt(double t, Context context) {
        boolean outer = context.isAbsorbed();
        try {
            return NoiseScale.noiseOf(body, context.withVariable(variable, t));
        } finally {
            context.setAbsorbed(outer);
        }
    }

    // last sample that lost something to rounding
    private static final class Losses {
        private double absorbedAt = Double.NaN;
    }

    @Override
    public <R> R accept(NodeVisitor<R> visitor) {
        return visitor.visitLimit(this);
    }
}
