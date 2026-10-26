package entity.node;

import entity.Context;
import entity.value.NumberValue;
import entity.value.Value;
import entity.value.Values;

import java.util.ArrayList;
import java.util.List;

public class PlusMinusNode extends Node {
    private final int signIndex;
    private final Node left;
    private final Node right;

    public PlusMinusNode(int signIndex, Node left, Node right) {
        this.signIndex = signIndex;
        this.left = left;
        this.right = right;
    }

    public int getSignIndex() {
        return signIndex;
    }

    @Override
    public List<Node> children() {
        List<Node> result = new ArrayList<>();
        if (left != null) {
            result.add(left);
        }
        result.add(right);
        return result;
    }

    @Override
    public Value evaluate(Context context) {
        double a = 0;
        if (left != null) {
            a = Values.toNumber(left.evaluate(context));
        }
        double b = Values.toNumber(right.evaluate(context));
        if (context.isPlus(signIndex)) {
            return new NumberValue(a + b);
        }
        return new NumberValue(a - b);
    }
}
