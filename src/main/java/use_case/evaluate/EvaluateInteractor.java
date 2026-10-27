package use_case.evaluate;

import entity.ConditionSolver;
import entity.Context;
import entity.EquationSolver;
import entity.Interval;
import entity.MathLexer;
import entity.MathParser;
import entity.MathSyntaxException;
import entity.UncalculableException;
import entity.UserFunction;
import entity.calculus.Rounding;
import entity.node.Node;
import entity.node.RelationNode;
import entity.value.FiniteSetValue;
import entity.value.NumberSetValue;
import entity.value.NumberValue;
import entity.value.Value;
import entity.value.Values;
import entity.value.VectorValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EvaluateInteractor implements EvaluateInputBoundary {
    private static final int MAX_PLUS_MINUS = 4;
    private static final int APPROXIMATE_DIGITS = 9;
    private static final double[] SIDE_PROBES = {0.5, -7.3, 2.9};
    private static final int MAX_ROOTS_BEYOND = 10;

    private final FunctionDataAccessInterface functionDataAccess;
    private final EvaluateOutputBoundary presenter;

    public EvaluateInteractor(FunctionDataAccessInterface functionDataAccess,
                              EvaluateOutputBoundary presenter) {
        this.functionDataAccess = functionDataAccess;
        this.presenter = presenter;
    }

    @Override
    public void execute(EvaluateInputData inputData) {
        String expression = inputData.getExpression();
        try {
            List<String> tokens = MathLexer.tokenize(expression);

            if (!tokens.isEmpty() && tokens.get(tokens.size() - 1).equals("=")) {
                tokens.remove(tokens.size() - 1);
            }
            if (tokens.isEmpty()) {
                presenter.prepareFailView("Please enter an expression.");
                return;
            }
            if (isFunctionDefinition(tokens)) {
                defineFunction(tokens, expression);
                return;
            }
            if (tokens.get(0).equals("=")) {
                presenter.prepareFailView("There is nothing on the left of \"=\".");
                return;
            }
            if (tokens.get(tokens.size() - 1).equals("=")) {
                presenter.prepareFailView("There is nothing on the right of \"=\".");
                return;
            }
            evaluate(tokens);
        } catch (MathSyntaxException e) {
            presenter.prepareFailView(e.getMessage());
        } catch (RuntimeException | StackOverflowError e) {
            presenter.prepareFailView("Uncalculable");
        }
    }

    private void evaluate(List<String> tokens) {
        Map<String, UserFunction> functions = functionDataAccess.getAllFunctions();
        MathParser parser = new MathParser(tokens, functions);
        Node node = parser.parse();
        int masks = signMaskCount(parser);
        Set<String> freeVariables = new LinkedHashSet<>();
        node.collectFreeVariables(freeVariables);
        List<String> unknowns = new ArrayList<>(freeVariables);

        if (unknowns.isEmpty()) {
            presenter.prepareValuesView(calculateAll(node, functions, masks));
        } else if (countEquals(node) > 1) {
            presenter.prepareFailView("Please use only one \"=\".");
        } else if (!node.isCondition()) {
            String name = unknowns.get(0);
            presenter.prepareFailView(unknownVariableMessage(name));
        } else if (unknowns.size() > 1) {
            presenter.prepareFailView("This equation has more than one unknown (" + String.join(", ", unknowns)
                    + "). Only one unknown is supported.");
        } else {
            solveCondition(node, unknowns.get(0), functions, masks);
        }
    }

    private Value calculate(Node node, Context context) {
        Value value = node.evaluate(context);
        if (value instanceof NumberValue && Double.isInfinite(((NumberValue) value).getValue())
                && !node.canBeInfinite()) {
            throw new UncalculableException();
        }
        if (value instanceof NumberSetValue) {
            throw new UncalculableException();
        }
        return value;
    }

    private int signMaskCount(MathParser parser) {
        if (parser.getPlusMinusCount() > MAX_PLUS_MINUS) {
            throw new UncalculableException();
        }
        return 1 << parser.getPlusMinusCount();
    }

    private List<Value> calculateAll(Node node, Map<String, UserFunction> functions, int masks) {
        List<Value> values = new ArrayList<>();
        for (int mask = 0; mask < masks; mask++) {
            Value value = calculate(node, new Context(functions).withSigns(mask));
            if (node.isApproximate()) {
                value = rounded(value);
            }
            boolean seen = false;
            for (Value existing : values) {
                if (Values.areEqual(existing, value)) {
                    seen = true;
                }
            }
            if (!seen) {
                values.add(value);
            }
        }
        return values;
    }

    private Value rounded(Value value) {
        if (value instanceof NumberValue) {
            return new NumberValue(round(((NumberValue) value).getValue()));
        }
        if (value instanceof VectorValue) {
            List<Double> components = new ArrayList<>();
            for (double component : ((VectorValue) value).getComponents()) {
                components.add(round(component));
            }
            return new VectorValue(components);
        }
        if (value instanceof FiniteSetValue) {
            List<Double> elements = new ArrayList<>();
            for (double element : ((FiniteSetValue) value).getElements()) {
                elements.add(round(element));
            }
            return new FiniteSetValue(elements);
        }
        return value;
    }

    private double round(double number) {
        return Rounding.toSignificant(number, APPROXIMATE_DIGITS);
    }

    private void solveCondition(Node node, String variable, Map<String, UserFunction> functions, int masks) {
        if (!isSingleEquation(node)) {
            solveInequality(node, variable, functions, masks);
            return;
        }
        boolean excluding = ((RelationNode) node).getOperators().get(0).equals("\\neq");
        List<Node> sides = ((RelationNode) node).getOperands();
        if (!hasNumberSides(sides, variable, functions)) {
            // sets/vectors need the condition solver
            solveInequality(node, variable, functions, masks);
            return;
        }
        List<Double> all = new ArrayList<>();
        boolean stretch = false;
        for (int mask = 0; mask < masks; mask++) {
            Context context = new Context(functions).withSigns(mask);
            EquationSolver solver = new EquationSolver(sides.get(0), sides.get(1), variable, context);
            if (solver.isAlwaysTrue()) {
                if (excluding) {
                    presenter.prepareSolutionsView(variable, new ArrayList<Double>());
                } else {
                    presenter.prepareAlwaysTrueView(variable);
                }
                return;
            }
            all.addAll(solver.solve());
            if (solver.isUndersampled()) {
                throw new UncalculableException();
            }
            stretch = stretch || solver.hasStretch();
        }
        Collections.sort(all);
        List<Double> solutions = new ArrayList<>();
        for (double solution : all) {
            if (solutions.isEmpty() || Math.abs(solution - solutions.get(solutions.size() - 1)) >= 1e-6) {
                solutions.add(solution);
            }
        }
        if (stretch && !excluding) {
            // equal on a whole stretch, intervals only
            solveInequality(node, variable, functions, masks);
        } else if (!excluding) {
            presenter.prepareSolutionsView(variable, solutions.isEmpty() && !stretch
                    ? rootsBeyond(node, variable, functions, masks) : solutions);
        } else if (masks > 1) {
            excludeWithSigns(node, variable, functions, masks);
        } else {
            excludeUndefinedPoints(node, variable, functions, masks, solutions);
        }
    }

    private boolean hasNumberSides(List<Node> sides, String variable, Map<String, UserFunction> functions) {
        Context context = new Context(functions);
        for (double point : SIDE_PROBES) {
            for (Node side : sides) {
                try {
                    Value value = side.evaluate(context.withVariable(variable, point));
                    if (!(value instanceof NumberValue)) {
                        return false;
                    }
                } catch (UncalculableException e) {
                    // undefined here, try another point
                }
            }
        }
        return true;
    }

    // no root in range -> use roots past it if few (R31)
    private List<Double> rootsBeyond(Node node, String variable, Map<String, UserFunction> functions, int masks) {
        List<Double> all = new ArrayList<>();
        for (int mask = 0; mask < masks; mask++) {
            List<Double> roots = new ConditionSolver(node, variable, new Context(functions).withSigns(mask))
                    .rootsBeyond(MAX_ROOTS_BEYOND);
            if (roots == null) {
                throw new UncalculableException();
            }
            all.addAll(roots);
        }
        Collections.sort(all);
        List<Double> roots = new ArrayList<>();
        for (double root : all) {
            if (roots.isEmpty() || !isSame(root, roots.get(roots.size() - 1))) {
                roots.add(root);
            }
        }
        if (roots.size() > MAX_ROOTS_BEYOND) {
            throw new UncalculableException();
        }
        return roots;
    }

    // exclude where a side is undefined too
    private void excludeUndefinedPoints(Node node, String variable, Map<String, UserFunction> functions,
                                        int masks, List<Double> solutions) {
        if (masks > 1) {
            throw new UncalculableException();
        }
        ConditionSolver solver = new ConditionSolver(node, variable, new Context(functions));
        if (solutions.isEmpty()) {
            List<Double> beyond = rootsBeyond(node, variable, functions, masks);
            if (!beyond.isEmpty()) {
                // x != 200: true in range, false only past it
                List<Interval> inRange = solver.solveInRange();
                if (inRange.size() != 1 || inRange.get(0).getLow() != Double.NEGATIVE_INFINITY
                        || inRange.get(0).getHigh() != Double.POSITIVE_INFINITY) {
                    throw new UncalculableException();
                }
                presenter.prepareExcludedView(variable, beyond);
                return;
            }
        }
        List<Interval> intervals = solver.solve();
        if (!solutions.isEmpty() && isEverythingBut(intervals, solutions)) {
            presenter.prepareExcludedView(variable, solutions);
        } else {
            showIntervals(variable, intervals);
        }
    }

    // \pm: exclude points from every sign choice
    private void excludeWithSigns(Node node, String variable, Map<String, UserFunction> functions, int masks) {
        List<Double> all = new ArrayList<>();
        for (int mask = 0; mask < masks; mask++) {
            Context context = new Context(functions).withSigns(mask);
            List<Interval> intervals = new ConditionSolver(node, variable, context).solve();
            if (intervals.isEmpty() || intervals.get(0).getLow() != Double.NEGATIVE_INFINITY
                    || intervals.get(intervals.size() - 1).getHigh() != Double.POSITIVE_INFINITY) {
                throw new UncalculableException();
            }
            List<Double> points = new ArrayList<>();
            for (int i = 1; i < intervals.size(); i++) {
                points.add(intervals.get(i).getLow());
            }
            if (!isEverythingBut(intervals, points)) {
                throw new UncalculableException();
            }
            all.addAll(points);
        }
        Collections.sort(all);
        List<Double> excluded = new ArrayList<>();
        for (double point : all) {
            if (excluded.isEmpty() || !isSame(point, excluded.get(excluded.size() - 1))) {
                excluded.add(point);
            }
        }
        presenter.prepareExcludedView(variable, excluded);
    }

    private boolean isEverythingBut(List<Interval> intervals, List<Double> points) {
        if (intervals.size() != points.size() + 1) {
            return false;
        }
        for (int i = 0; i < intervals.size(); i++) {
            Interval interval = intervals.get(i);
            double low = i == 0 ? Double.NEGATIVE_INFINITY : points.get(i - 1);
            double high = i == points.size() ? Double.POSITIVE_INFINITY : points.get(i);
            if (interval.isLowIncluded() || interval.isHighIncluded() || !isSame(interval.getLow(), low)
                    || !isSame(interval.getHigh(), high)) {
                return false;
            }
        }
        return true;
    }

    private boolean isSame(double a, double b) {
        return a == b || Math.abs(a - b) <= 1e-6 * Math.max(1, Math.abs(b));
    }

    private void solveInequality(Node node, String variable, Map<String, UserFunction> functions, int masks) {
        if (masks > 1) {
            throw new UncalculableException();
        }
        showIntervals(variable, new ConditionSolver(node, variable, new Context(functions)).solve());
    }

    private void showIntervals(String variable, List<Interval> intervals) {
        if (intervals.size() == 1 && intervals.get(0).getLow() == Double.NEGATIVE_INFINITY
                && intervals.get(0).getHigh() == Double.POSITIVE_INFINITY) {
            presenter.prepareAlwaysTrueView(variable);
        } else {
            presenter.prepareIntervalsView(variable, intervals);
        }
    }

    private String unknownVariableMessage(String name) {
        return "Unknown variable \"" + name + "\". To solve for it, write an equation such as " + name + "+1=3.";
    }

    private void defineFunction(List<String> tokens, String expression) {
        int equalsIndex = tokens.indexOf("=");
        String name = tokens.get(0);
        List<String> parameters = new ArrayList<>();
        for (int i = 2; i < equalsIndex; i += 2) {
            parameters.add(tokens.get(i));
        }
        String header = name + "(" + String.join(",", parameters) + ")";
        List<String> right = new ArrayList<>(tokens.subList(equalsIndex + 1, tokens.size()));
        if (right.isEmpty()) {
            presenter.prepareFailView("Please write a formula after \"" + header + "=\".");
            return;
        }

        Map<String, UserFunction> functions = new HashMap<>(functionDataAccess.getAllFunctions());
        functions.remove(name);

        MathParser parser = new MathParser(right, functions);
        Node body = parser.parse();
        if (parser.getPlusMinusCount() > 0) {
            throw new UncalculableException();
        }
        Set<String> variables = new LinkedHashSet<>();
        body.collectFreeVariables(variables);
        for (String variable : variables) {
            if (!parameters.contains(variable)) {
                String noun = parameters.size() == 1 ? "variable " : "variables ";
                presenter.prepareFailView("The formula of " + header + " can only use the " + noun
                        + String.join(", ", parameters) + ", but it uses " + variable + ".");
                return;
            }
        }

        Context testContext = new Context(functions);
        for (String parameter : parameters) {
            testContext = testContext.withVariable(parameter, 1.0);
        }
        try {
            body.evaluate(testContext);
        } catch (UncalculableException e) {
            // can be undefined at 1 and still be fine
        }

        String formulaText = textAfterEquals(expression);
        functionDataAccess.saveFunction(new UserFunction(name, parameters, body, formulaText));
        presenter.prepareFunctionDefinedView(header + " = " + formulaText);
    }

    private boolean isFunctionDefinition(List<String> tokens) {
        int equalsIndex = tokens.indexOf("=");
        if (equalsIndex < 4 || equalsIndex % 2 != 0 || hasEqualsOutsideBraces(tokens, equalsIndex + 1)) {
            return false;
        }
        String name = tokens.get(0);
        if (!MathLexer.isLetter(name) || name.equals("e") || !tokens.get(1).equals("(")
                || !tokens.get(equalsIndex - 1).equals(")")) {
            return false;
        }
        Set<String> parameters = new HashSet<>();
        for (int i = 2; i < equalsIndex; i += 2) {
            String parameter = tokens.get(i);
            String separator = tokens.get(i + 1);
            if (!MathLexer.isLetter(parameter) || parameter.equals(name) || parameter.equals("e")
                    || !parameters.add(parameter)) {
                return false;
            }
            if (i + 1 < equalsIndex - 1 && !separator.equals(",")) {
                return false;
            }
        }
        return true;
    }

    // ignore the = in \sum_{i=1}
    private boolean hasEqualsOutsideBraces(List<String> tokens, int from) {
        int depth = 0;
        for (int i = from; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.equals("{")) {
                depth++;
            } else if (token.equals("}")) {
                depth--;
            } else if (token.equals("=") && depth <= 0) {
                return true;
            }
        }
        return false;
    }

    private boolean isSingleEquation(Node node) {
        if (!(node instanceof RelationNode)) {
            return false;
        }
        List<String> operators = ((RelationNode) node).getOperators();
        return operators.size() == 1 && (operators.get(0).equals("=") || operators.get(0).equals("\\neq")
                || operators.get(0).equals("\\approx"));
    }

    private int countEquals(Node node) {
        if (!(node instanceof RelationNode)) {
            return 0;
        }
        return Collections.frequency(((RelationNode) node).getOperators(), "=");
    }

    private String textAfterEquals(String expression) {
        String formula = expression.substring(expression.indexOf('=') + 1).trim();
        if (formula.endsWith("=")) {
            formula = formula.substring(0, formula.length() - 1).trim();
        }
        return formula;
    }
}
