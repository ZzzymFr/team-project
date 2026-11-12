package entity.symbolic;

import entity.node.FallbackDerivative;
import entity.node.NumericDerivative;
import entity.node.PointDerivative;

// d/dx at a point everyone gets: the written formula, ridders when it cant
public final class Derivatives {
    private static final PointDerivative STANDARD =
            new FallbackDerivative(new ExactDerivative(), new NumericDerivative());

    private Derivatives() {
    }

    public static PointDerivative standard() {
        return STANDARD;
    }
}
