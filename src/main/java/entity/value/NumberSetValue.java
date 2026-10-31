package entity.value;

import entity.UncalculableException;

public final class NumberSetValue extends Value {
    public enum Kind { NATURAL, INTEGER, RATIONAL, REAL, COMPLEX }

    private final Kind kind;
    private final int dimension;

    public NumberSetValue(Kind kind, int dimension) {
        this.kind = kind;
        this.dimension = dimension;
    }

    public Kind getKind() {
        return kind;
    }

    public int getDimension() {
        return dimension;
    }

    public boolean containsNumber(double number) {
        if (dimension != 1 || Double.isNaN(number) || Double.isInfinite(number)) {
            return false;
        }
        boolean integer = number == Math.floor(number);
        switch (kind) {
            case NATURAL:
                return integer && number >= 0;
            case INTEGER:
                return integer;
            case RATIONAL:
                if (!integer) {
                    throw new UncalculableException(UncalculableException.RATIONAL);
                }
                return true;
            default:
                return true;
        }
    }

    public boolean containsVector(VectorValue vector) {
        if (vector.getDimension() != dimension) {
            return false;
        }
        NumberSetValue component = new NumberSetValue(kind, 1);
        for (double value : vector.getComponents()) {
            if (!component.containsNumber(value)) {
                return false;
            }
        }
        return true;
    }

    public boolean isSubsetOf(NumberSetValue other) {
        return dimension == other.dimension && kind.ordinal() <= other.kind.ordinal();
    }
}
