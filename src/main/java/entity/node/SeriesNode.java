package entity.node;

import entity.Context;
import entity.UncalculableException;
import entity.UndefinedException;
import entity.calculus.SeriesCalculator;
import entity.value.NumberValue;
import entity.value.Value;
import entity.value.Values;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SeriesNode extends Node {
    private static final double INTEGER_TOLERANCE = 1e-9;

    private final boolean isProduct;
    private final String variable;
    private final Node lower;
    private final Node upper;
    private final Node body;

    public SeriesNode(boolean isProduct, String variable, Node lower, Node upper, Node body) {
        this.isProduct = isProduct;
        this.variable = variable;
        this.lower = lower;
        this.upper = upper;
        this.body = body;
    }

    public boolean isProduct() {
        return isProduct;
    }

    public String getVariable() {
        return variable;
    }

    public Node getLower() {
        return lower;
    }

    public Node getUpper() {
        return upper;
    }

    public Node getBody() {
        return body;
    }

    @Override
    public List<Node> children() {
        return Arrays.asList(lower, upper, body);
    }

    @Override
    public void collectFreeVariables(Set<String> names) {
        lower.collectFreeVariables(names);
        upper.collectFreeVariables(names);
        Set<String> inBody = new HashSet<>();
        body.collectFreeVariables(inBody);
        inBody.remove(variable);
        names.addAll(inBody);
    }

    @Override
    public boolean canBeInfinite() {
        return true;
    }

    // infinite series is approx, upper bound var can be inf
    @Override
    public boolean isApproximate() {
        Set<String> inUpper = new HashSet<>();
        upper.collectFreeVariables(inUpper);
        return upper.canBeInfinite() || !inUpper.isEmpty() || super.isApproximate();
    }

    @Override
    public Value evaluate(Context context) {
        long start = bound(Values.toNumber(lower.evaluate(context)));
        double top = Values.toNumber(upper.evaluate(context));
        long end = top == Double.POSITIVE_INFINITY ? Long.MAX_VALUE : bound(top);
        return new NumberValue(SeriesCalculator.series(n -> term(context, n), start, end, isProduct));
    }

    private double term(Context context, long n) {
        context.spendStep();
        return Values.toNumber(body.evaluate(context.withVariable(variable, n)));
    }

    private static long bound(double value) {
        double rounded = Math.rint(value);
        if (!(Math.abs(value - rounded) <= INTEGER_TOLERANCE)) {
            throw new UndefinedException(UncalculableException.SUM_BOUNDS);
        }
        if (Math.abs(rounded) >= 0x1p62) {
            throw new UncalculableException(UncalculableException.TOO_BIG);
        }
        return (long) rounded;
    }
}
