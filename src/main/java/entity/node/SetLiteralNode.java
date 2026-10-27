package entity.node;

import entity.Context;
import entity.UncalculableException;
import entity.UndefinedException;
import entity.value.FiniteSetValue;
import entity.value.Value;
import entity.value.Values;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SetLiteralNode extends Node {
    private final List<Node> elements;

    public SetLiteralNode(List<Node> elements) {
        this.elements = Collections.unmodifiableList(new ArrayList<>(elements));
    }

    @Override
    public List<Node> children() {
        return elements;
    }

    @Override
    public boolean canBeInfinite() {
        return false;
    }

    @Override
    public Value evaluate(Context context) {
        List<Double> numbers = new ArrayList<>();
        for (Node element : elements) {
            double number = Values.toNumber(element.evaluate(context));
            if (Double.isNaN(number)) {
                throw new UndefinedException();
            }
            if (Double.isInfinite(number)) {
                throw new UncalculableException();
            }
            numbers.add(number);
        }
        return new FiniteSetValue(numbers);
    }
}
