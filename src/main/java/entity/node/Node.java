package entity.node;

import entity.Context;
import entity.value.Value;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class Node {
    public abstract Value evaluate(Context context);

    public abstract <R> R accept(NodeVisitor<R> visitor);

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

    public boolean isApproximate() {
        for (Node child : children()) {
            if (child.isApproximate()) {
                return true;
            }
        }
        return false;
    }

    public boolean isCondition() {
        return false;
    }

    // any d/dx or f' in here
    public boolean containsDerivative() {
        for (Node child : children()) {
            if (child.containsDerivative()) {
                return true;
            }
        }
        return false;
    }
}
