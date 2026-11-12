package entity.node;

import entity.Context;

// first way if it can, else the second
public final class FallbackDerivative implements PointDerivative {
    private final PointDerivative first;
    private final PointDerivative second;

    public FallbackDerivative(PointDerivative first, PointDerivative second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public double[] at(Node body, String variable, int order, Context context) {
        double[] result = first.at(body, variable, order, context);
        return result != null ? result : second.at(body, variable, order, context);
    }

    @Override
    public boolean isExact(Node body, String variable, int order, Context context) {
        return first.isExact(body, variable, order, context) || second.isExact(body, variable, order, context);
    }
}
