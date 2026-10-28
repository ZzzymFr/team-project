package entity.node;

import entity.Context;
import entity.value.NumberValue;
import entity.value.Value;

public class NumberNode extends Node {
    private final NumberValue value;
    private final int decimals;

    public NumberNode(double value, int decimals) {
        this.value = new NumberValue(value);
        this.decimals = decimals;
    }

    public double getValue() {
        return value.getValue();
    }

    public int getDecimals() {
        return decimals;
    }

    @Override
    public Value evaluate(Context context) {
        return value;
    }
}
