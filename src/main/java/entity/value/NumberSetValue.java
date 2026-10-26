package entity.value;

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
}
