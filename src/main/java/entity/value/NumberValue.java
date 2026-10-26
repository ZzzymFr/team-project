package entity.value;

public final class NumberValue extends Value {
    private final double value;

    public NumberValue(double value) {
        this.value = value;
    }

    public double getValue() {
        return value;
    }
}
