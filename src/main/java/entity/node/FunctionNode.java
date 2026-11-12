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
    private final NamedFunction function;

    public FunctionNode(String name, Node argument, Node extra) {
        this.name = name;
        this.argument = argument;
        this.extra = extra;
        this.function = NamedFunction.of(name);
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
        if (function == null) {
            throw new UncalculableException();
        }
        if (extra == null) {
            return new NumberValue(function.apply(x));
        }
        return new NumberValue(function.apply(x, Values.toNumber(extra.evaluate(context))));
    }

    @Override
    public <R> R accept(NodeVisitor<R> visitor) {
        return visitor.visitFunction(this);
    }
}
