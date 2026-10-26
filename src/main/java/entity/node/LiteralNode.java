package entity.node;

import entity.Context;
import entity.value.Value;

public class LiteralNode extends Node {
    private final Value value;
    private final boolean infinite;

    public LiteralNode(Value value, boolean infinite) {
        this.value = value;
        this.infinite = infinite;
    }

    public Value getValue() {
        return value;
    }

    @Override
    public Value evaluate(Context context) {
        return value;
    }

    @Override
    public boolean canBeInfinite() {
        return infinite;
    }
}
