package entity.node;

import entity.ConditionSolver;
import entity.Context;
import entity.UndecidableException;
import entity.value.BooleanValue;
import entity.value.FiniteSetValue;
import entity.value.NumberSetValue;
import entity.value.Value;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class QuantifierNode extends Node {
    private static final int RANGE = 1000;
    private static final int NESTED_RANGE = 100;

    private final boolean isForAll;
    private final String variable;
    private final Node domain;
    private final Node body;

    public QuantifierNode(boolean isForAll, String variable, Node domain, Node body) {
        this.isForAll = isForAll;
        this.variable = variable;
        this.domain = domain;
        this.body = body;
    }

    public boolean isForAll() {
        return isForAll;
    }

    public String getVariable() {
        return variable;
    }

    public Node getDomain() {
        return domain;
    }

    public Node getBody() {
        return body;
    }

    @Override
    public List<Node> children() {
        if (domain == null) {
            return Collections.singletonList(body);
        }
        return Arrays.asList(domain, body);
    }

    @Override
    public void collectFreeVariables(Set<String> names) {
        if (domain != null) {
            domain.collectFreeVariables(names);
        }
        Set<String> inBody = new HashSet<>();
        body.collectFreeVariables(inBody);
        inBody.remove(variable);
        names.addAll(inBody);
    }

    @Override
    public boolean isCondition() {
        return true;
    }

    // inner samples judge their own rounding
    @Override
    public Value evaluate(Context context) {
        boolean absorbed = context.isAbsorbed();
        boolean tolerated = context.isTolerated();
        try {
            return decide(context);
        } finally {
            context.setAbsorbed(absorbed);
            context.setTolerated(tolerated);
        }
    }

    private Value decide(Context context) {
        Value set = domain == null ? new NumberSetValue(NumberSetValue.Kind.REAL, 1) : domain.evaluate(context);
        ConditionSolver solver = new ConditionSolver(body, variable, context);
        if (set instanceof FiniteSetValue) {
            List<Double> elements = ((FiniteSetValue) set).getElements();
            return BooleanValue.of(isForAll ? solver.isTrueForEvery(elements) : solver.isTrueForSome(elements));
        }
        if (!(set instanceof NumberSetValue) || ((NumberSetValue) set).getDimension() != 1) {
            throw new UndecidableException();
        }
        int range = context.getVariableValues().isEmpty() ? RANGE : NESTED_RANGE;
        switch (((NumberSetValue) set).getKind()) {
            case NATURAL:
            case INTEGER:
                boolean naturalOnly = ((NumberSetValue) set).getKind() == NumberSetValue.Kind.NATURAL;
                return BooleanValue.of(isForAll ? solver.isTrueForEveryInteger(naturalOnly, range)
                        : solver.isTrueForSomeInteger(naturalOnly, range));
            case RATIONAL:
                return BooleanValue.of(isForAll ? solver.isTrueEverywhereRational()
                        : solver.isTrueSomewhereRational());
            case REAL:
                return BooleanValue.of(isForAll ? solver.isTrueEverywhere() : solver.isTrueSomewhere());
            default:
                return BooleanValue.of(overComplexNumbers(solver));
        }
    }

    // only reals sampled, cant prove the opposite
    private boolean overComplexNumbers(ConditionSolver solver) {
        if (isForAll && !solver.isTrueEverywhere()) {
            return false;
        }
        if (!isForAll && solver.isTrueSomewhere()) {
            return true;
        }
        throw new UndecidableException();
    }
}
