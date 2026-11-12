package use_case.evaluate;

import entity.Context;
import entity.MathLexer;
import entity.MathParser;
import entity.MathSyntaxException;
import entity.SyntaxProblem;
import entity.UncalculableException;
import entity.UncalculableReason;
import entity.UserFunction;
import entity.node.Node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// f(x,y)=... lines: spot them, check the body, save it
final class FunctionDefiner {
    // e is euler, x is what every other line solves for
    private static final Set<String> NEVER_FUNCTIONS = Set.of("e", "x");

    private final FunctionDataAccessInterface functionDataAccess;
    private final EvaluateOutputBoundary presenter;

    FunctionDefiner(FunctionDataAccessInterface functionDataAccess, EvaluateOutputBoundary presenter) {
        this.functionDataAccess = functionDataAccess;
        this.presenter = presenter;
    }

    static boolean isDefinition(List<String> tokens) {
        int equalsIndex = tokens.indexOf("=");
        if (equalsIndex < 4 || equalsIndex % 2 != 0 || hasEqualsOutsideBraces(tokens, equalsIndex + 1)) {
            return false;
        }
        String name = tokens.get(0);
        if (!MathLexer.isLetter(name) || NEVER_FUNCTIONS.contains(name) || !tokens.get(1).equals("(")
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

    void define(List<String> tokens, String expression) {
        int equalsIndex = tokens.indexOf("=");
        String name = tokens.get(0);
        List<String> parameters = new ArrayList<>();
        for (int i = 2; i < equalsIndex; i += 2) {
            parameters.add(tokens.get(i));
        }
        String header = name + "(" + String.join(",", parameters) + ")";
        List<String> right = new ArrayList<>(tokens.subList(equalsIndex + 1, tokens.size()));
        if (right.isEmpty()) {
            throw new MathSyntaxException(SyntaxProblem.NO_BODY, header);
        }

        Map<String, UserFunction> functions = new HashMap<>(functionDataAccess.getAllFunctions());
        functions.remove(name);

        MathParser parser = new MathParser(right, functions);
        Node body = parser.parse();
        if (parser.getPlusMinusCount() > 0) {
            throw new UncalculableException(UncalculableReason.PLUS_MINUS_HERE);
        }
        Set<String> variables = new LinkedHashSet<>();
        body.collectFreeVariables(variables);
        for (String variable : variables) {
            if (!parameters.contains(variable)) {
                throw new MathSyntaxException(SyntaxProblem.BODY_VARIABLE, header, String.join(", ", parameters),
                        variable);
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

    // ignore the = in \sum_{i=1}
    private static boolean hasEqualsOutsideBraces(List<String> tokens, int from) {
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

    private static String textAfterEquals(String expression) {
        String formula = expression.substring(expression.indexOf('=') + 1).trim();
        if (formula.endsWith("=")) {
            formula = formula.substring(0, formula.length() - 1).trim();
        }
        return formula;
    }
}
