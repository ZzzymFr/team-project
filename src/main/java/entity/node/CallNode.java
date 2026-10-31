package entity.node;

import entity.Context;
import entity.MathSyntaxException;
import entity.UncalculableException;
import entity.UserFunction;
import entity.calculus.DerivativeCalculator;
import entity.value.NumberValue;
import entity.value.Value;
import entity.value.Values;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CallNode extends Node {
    private final String name;
    private final List<Node> arguments;
    private final int derivativeOrder;
    private final int derivativeParameter;
    private final Map<String, UserFunction> functions;
    private boolean checkingInfinity = false;
    private boolean checkingApproximate = false;

    public CallNode(String name, List<Node> arguments, int derivativeOrder, int derivativeParameter,
                    Map<String, UserFunction> functions) {
        this.name = name;
        this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
        this.derivativeOrder = derivativeOrder;
        this.derivativeParameter = derivativeParameter;
        this.functions = functions;
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
    public boolean canBeInfinite() {
        if (super.canBeInfinite()) {
            return true;
        }
        UserFunction function = functions.get(name);
        if (function == null || checkingInfinity) {
            return false;
        }
        checkingInfinity = true;
        try {
            return function.getBody().canBeInfinite();
        } finally {
            checkingInfinity = false;
        }
    }

    @Override
    public boolean isApproximate() {
        if (derivativeOrder > 0 || super.isApproximate()) {
            return true;
        }
        UserFunction function = functions.get(name);
        if (function == null || checkingApproximate) {
            return false;
        }
        checkingApproximate = true;
        try {
            return function.getBody().isApproximate();
        } finally {
            checkingApproximate = false;
        }
    }

    @Override
    public Value evaluate(Context context) {
        context.spendStep();
        UserFunction function = context.getFunction(name);
        if (function == null) {
            throw new MathSyntaxException("Unknown variable \"" + name + "\".");
        }
        List<String> parameters = function.getParameters();
        if (parameters.size() != arguments.size()) {
            String inputs = parameters.size() == 1 ? " input." : " inputs.";
            throw new MathSyntaxException(name + " needs " + parameters.size() + inputs);
        }
        if (derivativeOrder < 0 || derivativeOrder > DerivativeCalculator.MAX_ORDER
                || derivativeParameter < 0 || derivativeParameter >= parameters.size()) {
            throw new UncalculableException(derivativeOrder > DerivativeCalculator.MAX_ORDER
                    ? UncalculableException.MAX_DERIVATIVE : UncalculableException.NOT_AN_INPUT);
        }
        Context bound = context.forFunctionBody(name);
        double[] values = new double[arguments.size()];
        for (int i = 0; i < arguments.size(); i++) {
            values[i] = Values.toNumber(arguments.get(i).evaluate(context));
            bound = bound.withVariable(parameters.get(i), values[i]);
        }
        Node body = function.getBody();
        if (derivativeOrder == 0) {
            return body.evaluate(bound);
        }
        String parameter = parameters.get(derivativeParameter);
        Context fixed = bound;
        double[] result = DerivativeCalculator.derivativeWithError(
                t -> DerivativeNode.numberAt(body, fixed.withVariable(parameter, t)),
                values[derivativeParameter], derivativeOrder);
        context.noteApproximation(result[1]);
        return new NumberValue(result[0]);
    }
}
