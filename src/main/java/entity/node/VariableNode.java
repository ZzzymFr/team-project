package entity.node;

import entity.Context;
import entity.value.NumberValue;
import entity.value.Value;

import java.util.Set;

public class VariableNode extends Node {
    private final String name;

    public VariableNode(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public Value evaluate(Context context) {
        return new NumberValue(context.getVariable(name));
    }

    @Override
    public void collectFreeVariables(Set<String> names) {
        names.add(name);
    }
}
