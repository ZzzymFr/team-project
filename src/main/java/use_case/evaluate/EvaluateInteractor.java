package use_case.evaluate;

import entity.Context;
import entity.EquationSolver;
import entity.MathLexer;
import entity.MathParser;
import entity.MathSyntaxException;
import entity.UncalculableException;
import entity.UserFunction;
import entity.node.Node;
import entity.node.RelationNode;
import entity.value.NumberSetValue;
import entity.value.NumberValue;
import entity.value.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EvaluateInteractor implements EvaluateInputBoundary {
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
        Node node = new MathParser(tokens, functions).parse();
        Set<String> freeVariables = new LinkedHashSet<>();
        node.collectFreeVariables(freeVariables);
        List<String> unknowns = new ArrayList<>(freeVariables);

        if (unknowns.isEmpty()) {
            presenter.prepareValuesView(Collections.singletonList(calculate(node, new Context(functions))));
        } else if (countEquals(node) > 1) {
            presenter.prepareFailView("Please use only one \"=\".");
        } else if (!node.isCondition()) {
            String name = unknowns.get(0);
            presenter.prepareFailView("Unknown variable \"" + name + "\". To solve for it, write an equation such as "
                    + name + "+1=3.");
        } else if (unknowns.size() > 1) {
            presenter.prepareFailView("This equation has more than one unknown (" + String.join(", ", unknowns)
                    + "). Only one unknown is supported.");
        } else {
            solveCondition(node, unknowns.get(0), new Context(functions));
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

    private void solveCondition(Node node, String variable, Context context) {
        if (isSingleEquation(node)) {
            List<Node> sides = ((RelationNode) node).getOperands();
            EquationSolver solver = new EquationSolver(sides.get(0), sides.get(1), variable, context);
            if (solver.isAlwaysTrue()) {
                presenter.prepareAlwaysTrueView(variable);
            } else {
                presenter.prepareSolutionsView(variable, solver.solve());
            }
            return;
        }
        presenter.prepareFailView("Unknown variable \"" + variable + "\". To solve for it, write an equation such as "
                + variable + "+1=3.");
    }

    private void defineFunction(List<String> tokens, String expression) {
        int equalsIndex = tokens.indexOf("=");
        String name = tokens.get(0);
        List<String> parameters = new ArrayList<>();
        for (int i = 2; i < equalsIndex; i += 2) {
            parameters.add(tokens.get(i));
        }
        String header = name + "(" + String.join(", ", parameters) + ")";
        List<String> right = new ArrayList<>(tokens.subList(equalsIndex + 1, tokens.size()));
        if (right.isEmpty()) {
            presenter.prepareFailView("Please write a formula after \"" + header + "=\".");
            return;
        }

        Map<String, UserFunction> functions = new HashMap<>(functionDataAccess.getAllFunctions());
        functions.remove(name);

        Node body = new MathParser(right, functions).parse();
        Set<String> variables = new LinkedHashSet<>();
        body.collectFreeVariables(variables);
        for (String variable : variables) {
            if (!parameters.contains(variable)) {
                presenter.prepareFailView("The formula of " + header + " can only use the variable "
                        + String.join(", ", parameters) + ", but it uses " + variable + ".");
                return;
            }
        }

        Context testContext = new Context(functions);
        for (String parameter : parameters) {
            testContext = testContext.withVariable(parameter, 1.0);
        }
        body.evaluate(testContext);

        String formulaText = textAfterEquals(expression);
        functionDataAccess.saveFunction(new UserFunction(name, parameters, body, formulaText));
        presenter.prepareFunctionDefinedView(header + " = " + formulaText);
    }

    private boolean isFunctionDefinition(List<String> tokens) {
        int equalsIndex = tokens.indexOf("=");
        if (equalsIndex != 4 || equalsIndex != tokens.lastIndexOf("=")) {
            return false;
        }
        String name = tokens.get(0);
        String parameter = tokens.get(2);
        return MathLexer.isLetter(name) && tokens.get(1).equals("(")
                && MathLexer.isLetter(parameter) && tokens.get(3).equals(")")
                && !name.equals(parameter) && !name.equals("e") && !parameter.equals("e");
    }

    private boolean isSingleEquation(Node node) {
        return node instanceof RelationNode
                && ((RelationNode) node).getOperators().equals(Collections.singletonList("="));
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
