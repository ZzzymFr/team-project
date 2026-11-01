package entity.value;

public final class NumberValue extends Value {
    private final double value;

    public NumberValue(double value) {
        this.value = value;
    }

    public double getValue() {
        return value;
    }

    @Override
    public <R> R accept(ValueVisitor<R> visitor) {
        return visitor.visitNumber(this);
    }
}
