package entity.node;

import entity.Context;
import entity.UncalculableException;
import entity.value.NumberValue;
import entity.value.Value;
import entity.value.Values;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FunctionNode extends Node {
    private final String name;
    private final Node argument;
    private final Node extra;

    public FunctionNode(String name, Node argument, Node extra) {
        this.name = name;
        this.argument = argument;
        this.extra = extra;
    }

    public String getName() {
        return name;
    }

    public Node getArgument() {
        return argument;
    }

    public Node getExtra() {
        return extra;
    }

    @Override
    public List<Node> children() {
        if (extra == null) {
            return Collections.singletonList(argument);
        }
        return Arrays.asList(argument, extra);
    }

    @Override
    public Value evaluate(Context context) {
        double x = Values.toNumber(argument.evaluate(context));
        switch (name) {
            case "\\sin":
                return new NumberValue(Math.sin(x));
            case "\\cos":
                return new NumberValue(Math.cos(x));
            case "\\tan":
                if (Math.abs(Math.cos(x)) < 1e-12) {
                    return new NumberValue(Double.NaN);
                }
                return new NumberValue(Math.tan(x));
            case "\\ln":
                return new NumberValue(logarithm(x));
            case "\\log":
                return new NumberValue(log(x, context));
            case "\\sqrt":
                return new NumberValue(root(x, context));
            default:
                throw new UncalculableException();
        }
    }

    private double log(double x, Context context) {
        if (extra == null) {
            return x <= 0 ? Double.NaN : Math.log10(x);
        }
        double base = Values.toNumber(extra.evaluate(context));
        if (base <= 0 || base == 1) {
            return Double.NaN;
        }
        return logarithm(x) / Math.log(base);
    }

    private static double logarithm(double x) {
        if (x <= 0) {
            return Double.NaN;
        }
        return Math.log(x);
    }

    private double root(double x, Context context) {
        if (extra == null) {
            return Math.sqrt(x);
        }
        double degree = Values.toNumber(extra.evaluate(context));
        if (degree == 2) {
            return Math.sqrt(x);
        }
        boolean isOddWholeNumber = degree == Math.floor(degree) && Math.abs(degree % 2) == 1;
        if (x < 0 && isOddWholeNumber) {
            return -Math.pow(-x, 1 / degree);
        }
        return Math.pow(x, 1 / degree);
    }
}
