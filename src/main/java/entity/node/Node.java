package entity.node;

import entity.Context;
import entity.value.Value;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class Node {
    public abstract Value evaluate(Context context);

    public List<Node> children() {
        return Collections.emptyList();
    }

    public void collectFreeVariables(Set<String> names) {
        for (Node child : children()) {
            child.collectFreeVariables(names);
        }
    }

    public boolean canBeInfinite() {
        for (Node child : children()) {
            if (child.canBeInfinite()) {
                return true;
            }
        }
        return false;
    }

    public boolean isCondition() {
        return false;
    }
}
