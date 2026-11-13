package entity;

import entity.node.Node;
import entity.node.PointDerivative;
import entity.symbolic.Derivatives;
import entity.value.Value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class Context {
    public static final int MAX_STEPS = 3_000_000;
    private static final int MAX_FUNCTION_DEPTH = 50;

    private final Map<String, UserFunction> functions;
    private final Binding variables;
    private final int signMask;
    private final int depth;
    private final Budget budget;
    private final PointDerivative derivative;

    public Context(Map<String, UserFunction> functions) {
        this(functions, Derivatives.standard());
    }

    public Context(Map<String, UserFunction> functions, PointDerivative derivative) {
        this(functions, null, 0, 0, new Budget(), derivative);
    }

    private Context(Map<String, UserFunction> functions, Binding variables, int signMask, int depth,
                    Budget budget, PointDerivative derivative) {
        this.functions = functions;
        this.variables = variables;
        this.signMask = signMask;
        this.depth = depth;
        this.budget = budget;
        this.derivative = derivative;
    }

    public Context withVariable(String name, double value) {
        return new Context(functions, new Binding(name, value, variables), signMask, depth, budget, derivative);
    }

    public Context withSigns(int signMask) {
        return new Context(functions, variables, signMask, depth, budget, derivative);
    }

    public Context forFunctionBody() {
        return enterFunctionBody(null);
    }

    public Context forFunctionBody(String functionName) {
        return enterFunctionBody(functionName);
    }

    private Context enterFunctionBody(String name) {  // null = dont know which
        if (depth >= MAX_FUNCTION_DEPTH) {
            throw name == null ? new MathSyntaxException(SyntaxProblem.CALLS_ITSELF)
                    : new MathSyntaxException(SyntaxProblem.CALLS_ITSELF, name);
        }
        return new Context(functions, null, signMask, depth + 1, budget, derivative);
    }

    public boolean hasVariable(String name) {
        return find(name) != null;
    }

    public double getVariable(String name) {
        Binding binding = find(name);
        if (binding == null) {
            throw new MathSyntaxException(SyntaxProblem.UNKNOWN_VARIABLE, name);
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

    public PointDerivative getDerivative() {
        return derivative;
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

    // max rel err from approx since last reset
    public void noteApproximation(double error) {
        budget.approximation = Math.max(budget.approximation, error);
    }

    public double getApproximation() {
        return budget.approximation;
    }

    public void setApproximation(double error) {
        budget.approximation = error;
    }

    // node's answer only hangs on its free vars and the +- choice, so work it out once per those
    public Value once(Node node, List<String> freeVariables, Supplier<Value> compute) {
        double[] values = new double[freeVariables.size()];
        for (int i = 0; i < values.length; i++) {
            if (!hasVariable(freeVariables.get(i))) {
                return compute.get();
            }
            values[i] = getVariable(freeVariables.get(i));
        }
        SeenKey key = new SeenKey(node, signMask, values);
        Outcome known = budget.seen.get(key);
        if (known == null) {
            known = outcomeOf(compute);
            budget.seen.put(key, known);
        } else {
            known.charge(budget);
        }
        return known.replay(budget);
    }

    private Outcome outcomeOf(Supplier<Value> compute) {
        boolean absorbed = budget.absorbed;
        boolean tolerated = budget.tolerated;
        double approximation = budget.approximation;
        int steps = budget.steps;
        budget.absorbed = false;
        budget.tolerated = false;
        budget.approximation = 0;
        try {
            return new Outcome(compute.get(), null, budget, budget.steps - steps);
        } catch (BudgetExceededException e) {
            throw e;
        } catch (UncalculableException e) {
            return new Outcome(null, e, budget, budget.steps - steps);
        } finally {
            budget.absorbed = absorbed || budget.absorbed;
            budget.tolerated = tolerated || budget.tolerated;
            budget.approximation = Math.max(approximation, budget.approximation);
        }
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
        private double approximation;
        private final Map<SeenKey, Outcome> seen = new HashMap<>();
    }

    private static final class SeenKey {
        private final Node node;
        private final int signMask;
        private final double[] values;

        private SeenKey(Node node, int signMask, double[] values) {
            this.node = node;
            this.signMask = signMask;
            this.values = values;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof SeenKey)) {
                return false;
            }
            SeenKey key = (SeenKey) other;
            return key.node == node && key.signMask == signMask && Arrays.equals(key.values, values);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * System.identityHashCode(node) + signMask) + Arrays.hashCode(values);
        }
    }

    // value or failure, plus the flags and steps the first run left behind
    private static final class Outcome {
        private final Value value;
        private final UncalculableException failure;
        private final boolean absorbed;
        private final boolean tolerated;
        private final double approximation;
        private final int steps;

        private Outcome(Value value, UncalculableException failure, Budget budget, int steps) {
            this.value = value;
            this.failure = failure;
            this.absorbed = budget.absorbed;
            this.tolerated = budget.tolerated;
            this.approximation = budget.approximation;
            this.steps = steps;
        }

        // a hit costs the same steps as a rerun, so too-complex answers dont move
        private void charge(Budget budget) {
            budget.steps += steps;
            if (budget.steps > MAX_STEPS) {
                throw new BudgetExceededException();
            }
        }

        private Value replay(Budget budget) {
            budget.absorbed = budget.absorbed || absorbed;
            budget.tolerated = budget.tolerated || tolerated;
            budget.approximation = Math.max(budget.approximation, approximation);
            if (failure != null) {
                throw failure;
            }
            return value;
        }
    }
}
