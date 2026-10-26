package use_case.evaluate;

import entity.EquationSolver;
import entity.MathEvaluator;
import entity.MathLexer;
import entity.MathSyntaxException;
import entity.UserFunction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

            int equalsIndex = tokens.indexOf("=");
            if (equalsIndex == -1) {
                calculateExpression(tokens);
                return;
            }
            if (equalsIndex != tokens.lastIndexOf("=")) {
                presenter.prepareFailView("Please use only one \"=\".");
                return;
            }

            List<String> left = new ArrayList<>(tokens.subList(0, equalsIndex));
            List<String> right = new ArrayList<>(tokens.subList(equalsIndex + 1, tokens.size()));
            if (left.isEmpty()) {
                presenter.prepareFailView("There is nothing on the left of \"=\".");
            } else if (isFunctionDefinition(left)) {
                defineFunction(left, right, expression);
            } else {
                checkOrSolveEquation(left, right);
            }
        } catch (MathSyntaxException e) {
            presenter.prepareFailView(e.getMessage());
        }
    }

    private void calculateExpression(List<String> tokens) {
        Map<String, UserFunction> functions = functionDataAccess.getAllFunctions();
        List<String> unknowns = findVariables(tokens, functions);
        if (!unknowns.isEmpty()) {
            String name = unknowns.get(0);
            presenter.prepareFailView("Unknown variable \"" + name + "\". To solve for it, write an equation such as "
                    + name + "+1=3.");
            return;
        }
        double value = new MathEvaluator(tokens, new HashMap<>(), functions).evaluate();
        presenter.prepareNumberView(value);
    }

    private void defineFunction(List<String> left, List<String> right, String expression) {
        String name = left.get(0);
        String parameter = left.get(2);
        if (right.isEmpty()) {
            presenter.prepareFailView("Please write a formula after \"" + name + "(" + parameter + ")=\".");
            return;
        }

        Map<String, UserFunction> functions = new HashMap<>(functionDataAccess.getAllFunctions());
        functions.remove(name);

        for (String variable : findVariables(right, functions)) {
            if (!variable.equals(parameter)) {
                presenter.prepareFailView("The formula of " + name + "(" + parameter + ") can only use the variable "
                        + parameter + ", but it uses " + variable + ".");
                return;
            }
        }

        Map<String, Double> testValues = new HashMap<>();
        testValues.put(parameter, 1.0);
        new MathEvaluator(right, testValues, functions).evaluate();

        String formulaText = textAfterEquals(expression);
        functionDataAccess.saveFunction(new UserFunction(name, parameter, right, formulaText));
        presenter.prepareFunctionDefinedView(name + "(" + parameter + ") = " + formulaText);
    }

    private void checkOrSolveEquation(List<String> left, List<String> right) {
        if (right.isEmpty()) {
            presenter.prepareFailView("There is nothing on the right of \"=\".");
            return;
        }
        Map<String, UserFunction> functions = functionDataAccess.getAllFunctions();

        List<String> unknowns = findVariables(left, functions);
        for (String variable : findVariables(right, functions)) {
            if (!unknowns.contains(variable)) {
                unknowns.add(variable);
            }
        }

        if (unknowns.isEmpty()) {
            double leftValue = new MathEvaluator(left, new HashMap<>(), functions).evaluate();
            double rightValue = new MathEvaluator(right, new HashMap<>(), functions).evaluate();
            presenter.prepareCheckView(isAlmostEqual(leftValue, rightValue), leftValue, rightValue);
        } else if (unknowns.size() == 1) {
            String variable = unknowns.get(0);
            EquationSolver solver = new EquationSolver(left, right, variable, functions);
            if (solver.isAlwaysTrue()) {
                presenter.prepareAlwaysTrueView(variable);
            } else {
                presenter.prepareSolutionsView(variable, solver.solve());
            }
        } else {
            presenter.prepareFailView("This equation has more than one unknown (" + String.join(", ", unknowns)
                    + "). Only one unknown is supported.");
        }
    }

    private boolean isFunctionDefinition(List<String> left) {
        if (left.size() != 4) {
            return false;
        }
        String name = left.get(0);
        String parameter = left.get(2);
        return MathLexer.isLetter(name) && left.get(1).equals("(")
                && MathLexer.isLetter(parameter) && left.get(3).equals(")")
                && !name.equals(parameter) && !name.equals("e") && !parameter.equals("e");
    }

    private List<String> findVariables(List<String> tokens, Map<String, UserFunction> functions) {
        List<String> names = new ArrayList<>();
        for (String token : tokens) {
            if (MathLexer.isLetter(token) && !token.equals("e")
                    && !functions.containsKey(token) && !names.contains(token)) {
                names.add(token);
            }
        }
        return names;
    }

    private boolean isAlmostEqual(double a, double b) {
        double size = Math.max(1, Math.max(Math.abs(a), Math.abs(b)));
        return Math.abs(a - b) <= 1e-9 * size;
    }

    private String textAfterEquals(String expression) {
        String formula = expression.substring(expression.indexOf('=') + 1).trim();
        if (formula.endsWith("=")) {
            formula = formula.substring(0, formula.length() - 1).trim();
        }
        return formula;
    }
}
