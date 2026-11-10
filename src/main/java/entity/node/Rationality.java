package entity.node;

import entity.symbolic.Rational;

// exact p/q, surely irrational, or cant tell
final class Rationality {
    static final Rationality IRRATIONAL = new Rationality(null);
    static final Rationality UNKNOWN = new Rationality(null);

    private final Rational value;

    private Rationality(Rational value) {
        this.value = value;
    }

    static Rationality exact(Rational value) {
        return new Rationality(value);
    }

    boolean isExact() {
        return value != null;
    }

    boolean isIrrational() {
        return this == IRRATIONAL;
    }

    Rational getValue() {
        return value;
    }
}
