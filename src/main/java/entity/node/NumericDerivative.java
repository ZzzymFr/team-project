package entity.node;

import entity.Context;
import entity.calculus.DerivativeCalculator;

// ridders on samples of the body, always gives something
public final class NumericDerivative implements PointDerivative {
    @Override
    public double[] at(Node body, String variable, int order, Context context) {
        double x = context.getVariable(variable);
        return DerivativeCalculator.derivativeWithError(
                t -> DerivativeNode.numberAt(body, context.withVariable(variable, t)), x, order);
    }

    @Override
    public boolean isExact(Node body, String variable, int order, Context context) {
        return false;
    }
}
