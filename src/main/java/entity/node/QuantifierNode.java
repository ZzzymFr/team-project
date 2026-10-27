package entity.node;

import entity.BudgetExceededException;
import entity.ConditionSolver;
import entity.Context;
import entity.UncalculableException;
import entity.value.BooleanValue;
import entity.value.FiniteSetValue;
import entity.value.NumberSetValue;
import entity.value.Value;
import entity.value.Values;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class QuantifierNode extends Node {
    private static final int RANGE = 1000;
    private static final int NESTED_RANGE = 100;
    private static final double MAX_EXACT = 0x1p53;

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

    @Override
    public Value evaluate(Context context) {
        Value set = domain == null ? new NumberSetValue(NumberSetValue.Kind.REAL, 1) : domain.evaluate(context);
        if (set instanceof FiniteSetValue) {
            return BooleanValue.of(checkAll(context, ((FiniteSetValue) set).getElements()));
        }
        if (!(set instanceof NumberSetValue) || ((NumberSetValue) set).getDimension() != 1) {
            throw new UncalculableException();
        }
        int range = context.getVariableValues().isEmpty() ? RANGE : NESTED_RANGE;
        switch (((NumberSetValue) set).getKind()) {
            case NATURAL:
                return BooleanValue.of(checkAll(context, integers(context, 0, range)));
            case INTEGER:
                return BooleanValue.of(checkAll(context, integers(context, -range, range)));
            case RATIONAL:
                ConditionSolver overRationals = new ConditionSolver(body, variable, context);
                return BooleanValue.of(isForAll ? overRationals.isTrueEverywhereRational()
                        : overRationals.isTrueSomewhereRational());
            case REAL:
                ConditionSolver overReals = new ConditionSolver(body, variable, context);
                return BooleanValue.of(isForAll ? overReals.isTrueEverywhere() : overReals.isTrueSomewhere());
            default:
                return BooleanValue.of(overComplexNumbers(context));
        }
    }

    // only reals sampled, cant prove the opposite
    private boolean overComplexNumbers(Context context) {
        ConditionSolver solver = new ConditionSolver(body, variable, context);
        if (isForAll && !solver.isTrueEverywhere()) {
            return false;
        }
        if (!isForAll && solver.isTrueSomewhere()) {
            return true;
        }
        throw new UncalculableException();
    }

    // from..to plus ints around outer values
    private static List<Double> integers(Context context, int from, int to) {
        List<Double> values = new ArrayList<>();
        for (int n = from; n <= to; n++) {
            values.add((double) n);
        }
        for (double value : context.getVariableValues()) {
            if (Math.abs(value) > MAX_EXACT) {
                continue;
            }
            double[] near = {Math.floor(value) - 1, Math.floor(value), Math.ceil(value), Math.ceil(value) + 1,
                    -Math.floor(value), -Math.ceil(value)};
            for (double n : near) {
                if ((n < from || n > to) && (from < 0 || n >= 0) && !values.contains(n)) {
                    values.add(n);
                }
            }
        }
        return values;
    }

    private boolean checkAll(Context context, List<Double> elements) {
        for (double element : elements) {
            if (isTrueAt(context, element) != isForAll) {
                return !isForAll;
            }
        }
        return isForAll;
    }

    // undefined or non bool body doesnt count
    private boolean isTrueAt(Context context, double value) {
        context.spendStep();
        try {
            return Values.toBoolean(body.evaluate(context.withVariable(variable, value)));
        } catch (BudgetExceededException e) {
            throw e;
        } catch (UncalculableException e) {
            return false;
        }
    }
}
