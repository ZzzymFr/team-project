package entity.node;

import entity.Context;
import entity.MathSyntaxException;
import entity.UncalculableException;
import entity.UserFunction;
import entity.value.Value;
import entity.value.Values;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CallNode extends Node {
    private final String name;
    private final List<Node> arguments;
    private final int derivativeOrder;
    private final int derivativeParameter;

    public CallNode(String name, List<Node> arguments, int derivativeOrder, int derivativeParameter) {
        this.name = name;
        this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
        this.derivativeOrder = derivativeOrder;
        this.derivativeParameter = derivativeParameter;
    }

    public String getName() {
        return name;
    }

    public List<Node> getArguments() {
        return arguments;
    }

    public int getDerivativeOrder() {
        return derivativeOrder;
    }

    public int getDerivativeParameter() {
        return derivativeParameter;
    }

    @Override
    public List<Node> children() {
        return arguments;
    }

    @Override
    public Value evaluate(Context context) {
        context.spendStep();
        UserFunction function = context.getFunction(name);
        if (function == null) {
            throw new MathSyntaxException("Unknown variable \"" + name + "\".");
        }
        List<String> parameters = function.getParameters();
        if (derivativeOrder != 0 || parameters.size() != arguments.size()) {
            throw new UncalculableException();
        }
        Context body = context.forFunctionBody(name);
        for (int i = 0; i < arguments.size(); i++) {
            double argument = Values.toNumber(arguments.get(i).evaluate(context));
            body = body.withVariable(parameters.get(i), argument);
        }
        return function.getBody().evaluate(body);
    }
}
