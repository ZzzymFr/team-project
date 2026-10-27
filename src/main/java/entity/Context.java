package entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Context {
    public static final int MAX_STEPS = 3_000_000;
    private static final int MAX_FUNCTION_DEPTH = 50;

    private final Map<String, UserFunction> functions;
    private final Binding variables;
    private final int signMask;
    private final int depth;
    private final Budget budget;

    public Context(Map<String, UserFunction> functions) {
        this(functions, null, 0, 0, new Budget());
    }

    private Context(Map<String, UserFunction> functions, Binding variables, int signMask, int depth,
                    Budget budget) {
        this.functions = functions;
        this.variables = variables;
        this.signMask = signMask;
        this.depth = depth;
        this.budget = budget;
    }

    public Context withVariable(String name, double value) {
        return new Context(functions, new Binding(name, value, variables), signMask, depth, budget);
    }

    public Context withSigns(int signMask) {
        return new Context(functions, variables, signMask, depth, budget);
    }

    public Context forFunctionBody() {
        return enterFunctionBody("A function");
    }

    public Context forFunctionBody(String functionName) {
        return enterFunctionBody("The function " + functionName);
    }

    private Context enterFunctionBody(String subject) {
        if (depth >= MAX_FUNCTION_DEPTH) {
            throw new MathSyntaxException(subject + " keeps calling itself, so it cannot be calculated.");
        }
        return new Context(functions, null, signMask, depth + 1, budget);
    }

    public boolean hasVariable(String name) {
        return find(name) != null;
    }

    public double getVariable(String name) {
        Binding binding = find(name);
        if (binding == null) {
            throw new MathSyntaxException("Unknown variable \"" + name + "\".");
        }
        return binding.value;
    }

    public Collection<Double> getVariableValues() {
        List<Double> values = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Binding binding = variables; binding != null; binding = binding.next) {
            if (seen.add(binding.name)) {
                values.add(binding.value);
            }
        }
        return values;
    }

    public boolean hasFunction(String name) {
        return functions.containsKey(name);
    }

    public UserFunction getFunction(String name) {
        return functions.get(name);
    }

    public boolean isPlus(int signIndex) {
        return ((signMask >> signIndex) & 1) == 0;
    }

    public void spendStep() {
        budget.steps++;
        if (budget.steps > MAX_STEPS) {
            throw new BudgetExceededException();
        }
    }

    // set when something lost to rounding (x+1=x at 1e16)
    public void markAbsorbed() {
        budget.absorbed = true;
    }

    public boolean isAbsorbed() {
        return budget.absorbed;
    }

    public void setAbsorbed(boolean absorbed) {
        budget.absorbed = absorbed;
    }

    // set when tol decides the comparison
    public void markTolerated() {
        budget.tolerated = true;
    }

    public boolean isTolerated() {
        return budget.tolerated;
    }

    public void setTolerated(boolean tolerated) {
        budget.tolerated = tolerated;
    }

    private Binding find(String name) {
        for (Binding binding = variables; binding != null; binding = binding.next) {
            if (binding.name.equals(name)) {
                return binding;
            }
        }
        return null;
    }

    private static final class Binding {
        private final String name;
        private final double value;
        private final Binding next;

        private Binding(String name, double value, Binding next) {
            this.name = name;
            this.value = value;
            this.next = next;
        }
    }

    private static final class Budget {
        private int steps;
        private boolean absorbed;
        private boolean tolerated;
    }
}
