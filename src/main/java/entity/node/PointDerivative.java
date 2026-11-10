package entity.node;

import entity.Context;

// d^n body / d variable^n at the values in the context
public interface PointDerivative {
    // {value, rel error}, null if this way cant do it
    double[] at(Node body, String variable, int order, Context context);

    // true if at() adds no error of its own for this body
    boolean isExact(Node body, String variable, int order, Context context);
}
