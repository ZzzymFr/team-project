package entity.node;

import entity.Context;
import entity.UncalculableException;
import entity.UndefinedException;
import entity.value.Value;
import entity.value.Values;
import entity.value.VectorValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VectorNode extends Node {
    private final List<Node> components;

    public VectorNode(List<Node> components) {
        this.components = Collections.unmodifiableList(new ArrayList<>(components));
    }

    public List<Node> getComponents() {
        return components;
    }

    @Override
    public List<Node> children() {
        return components;
    }

    @Override
    public boolean canBeInfinite() {
        return false;
    }

    @Override
    public Value evaluate(Context context) {
        List<Double> numbers = new ArrayList<>();
        for (Node component : components) {
            double number = Values.toNumber(component.evaluate(context));
            if (Double.isNaN(number)) {
                throw new UndefinedException();
            }
            if (Double.isInfinite(number)) {
                throw new UncalculableException(UncalculableException.TOO_BIG);
            }
            numbers.add(number);
        }
        return new VectorValue(numbers);
    }
}
