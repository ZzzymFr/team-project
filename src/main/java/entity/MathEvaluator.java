package entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MathEvaluator {
    private static final int MAX_FUNCTION_DEPTH = 50;

    private final List<String> tokens;
    private final Map<String, Double> variables;
    private final Map<String, UserFunction> functions;
    private final int depth;
    private int position = 0;

    public MathEvaluator(List<String> tokens, Map<String, Double> variables,
                         Map<String, UserFunction> functions) {
        this(tokens, variables, functions, 0);
    }

    private MathEvaluator(List<String> tokens, Map<String, Double> variables,
                          Map<String, UserFunction> functions, int depth) {
        this.tokens = tokens;
        this.variables = variables;
        this.functions = functions;
        this.depth = depth;
    }

    public double evaluate() {
        if (tokens.isEmpty()) {
            throw new MathSyntaxException("The expression is empty.");
        }
        double result = parseExpression();
        if (position < tokens.size()) {
            throw new MathSyntaxException("Unexpected \"" + peek() + "\".");
        }
        return result;
    }

    private double parseExpression() {
        double result = parseTerm();
        while (peekIs("+") || peekIs("-")) {
            String operator = next();
            double right = parseTerm();
            if (operator.equals("+")) {
                result = result + right;
            } else {
                result = result - right;
            }
        }
        return result;
    }

    private double parseTerm() {
        double result = parseUnary();
        while (true) {
            if (peekIs("*") || peekIs("\\cdot") || peekIs("\\times")) {
                next();
                result = result * parseUnary();
            } else if (peekIs("/")) {
                next();
                result = result / parseUnary();
            } else if (startsValue(peek())) {
                result = result * parsePower();
            } else {
                return result;
            }
        }
    }

    private double parseUnary() {
        if (peekIs("-")) {
            next();
            return -parseUnary();
        }
        if (peekIs("+")) {
            next();
            return parseUnary();
        }
        return parsePower();
    }

    private double parsePower() {
        double base = parseFactorial();
        if (peekIs("^")) {
            next();
            double exponent = parseUnary();
            return Math.pow(base, exponent);
        }
        return base;
    }

    private double parseFactorial() {
        double value = parsePrimary();
        while (peekIs("!")) {
            next();
            value = factorial(value);
        }
        return value;
    }

    private double parsePrimary() {
        String token = next();

        if (isNumber(token)) {
            return parseNumber(token);
        }
        if (token.equals("(")) {
            return parseRestOfGroup(")");
        }
        if (token.equals("{")) {
            return parseRestOfGroup("}");
        }
        if (token.equals("[")) {
            return parseRestOfGroup("]");
        }
        if (token.equals("|")) {
            return Math.abs(parseRestOfGroup("|"));
        }
        if (token.equals("\\pi")) {
            return Math.PI;
        }
        if (token.equals("e")) {
            return Math.E;
        }
        if (token.equals("\\frac")) {
            expect("{");
            double top = parseRestOfGroup("}");
            expect("{");
            double bottom = parseRestOfGroup("}");
            return top / bottom;
        }
        if (token.equals("\\sqrt")) {
            return parseSqrt();
        }
        if (token.equals("\\log")) {
            return parseLog();
        }
        if (token.equals("\\sin")) {
            return Math.sin(parseFunctionArgument());
        }
        if (token.equals("\\cos")) {
            return Math.cos(parseFunctionArgument());
        }
        if (token.equals("\\tan")) {
            return Math.tan(parseFunctionArgument());
        }
        if (token.equals("\\ln")) {
            return Math.log(parseFunctionArgument());
        }
        if (MathLexer.isLetter(token)) {
            return parseLetter(token);
        }
        if (token.startsWith("\\") && token.length() > 1) {
            throw new MathSyntaxException("\"" + token + "\" is not supported.");
        }
        throw new MathSyntaxException("Unexpected \"" + token + "\".");
    }

    private double parseSqrt() {
        double degree = 2;
        if (peekIs("[")) {
            next();
            degree = parseRestOfGroup("]");
        }
        double value = parseFunctionArgument();

        if (degree == 2) {
            return Math.sqrt(value);
        }
        boolean isOddWholeNumber = degree == Math.floor(degree) && Math.abs(degree % 2) == 1;
        if (value < 0 && isOddWholeNumber) {
            return -Math.pow(-value, 1 / degree);
        }
        return Math.pow(value, 1 / degree);
    }

    private double parseLog() {
        if (peekIs("_")) {
            next();
            double base = parsePrimary();
            double value = parseFunctionArgument();
            return Math.log(value) / Math.log(base);
        }
        return Math.log10(parseFunctionArgument());
    }

    private double parseFunctionArgument() {
        if (peekIs("(")) {
            next();
            return parseRestOfGroup(")");
        }
        if (peekIs("{")) {
            next();
            return parseRestOfGroup("}");
        }
        return parseUnary();
    }

    private double parseLetter(String name) {
        if (functions.containsKey(name)) {
            if (!peekIs("(")) {
                throw new MathSyntaxException("Write " + name + "(...) to use the function " + name + ".");
            }
            next();
            double argument = parseRestOfGroup(")");
            return callUserFunction(functions.get(name), argument);
        }
        if (variables.containsKey(name)) {
            return variables.get(name);
        }
        throw new MathSyntaxException("Unknown variable \"" + name + "\".");
    }

    private double callUserFunction(UserFunction function, double argument) {
        if (depth >= MAX_FUNCTION_DEPTH) {
            throw new MathSyntaxException("The function " + function.getName()
                    + " keeps calling itself, so it cannot be calculated.");
        }
        Map<String, Double> bodyVariables = new HashMap<>();
        bodyVariables.put(function.getParameter(), argument);
        MathEvaluator bodyEvaluator = new MathEvaluator(function.getBody(), bodyVariables, functions, depth + 1);
        return bodyEvaluator.evaluate();
    }

    private double parseRestOfGroup(String closingBracket) {
        double value = parseExpression();
        expect(closingBracket);
        return value;
    }

    private double parseNumber(String token) {
        try {
            return Double.parseDouble(token);
        } catch (NumberFormatException e) {
            throw new MathSyntaxException("\"" + token + "\" is not a valid number.");
        }
    }

    private double factorial(double n) {
        if (n < 0 || n != Math.floor(n)) {
            return Double.NaN;
        }
        if (n > 170) {
            return Double.POSITIVE_INFINITY;
        }
        double result = 1;
        for (int i = 2; i <= n; i++) {
            result = result * i;
        }
        return result;
    }

    private boolean startsValue(String token) {
        if (token == null) {
            return false;
        }
        return isNumber(token) || MathLexer.isLetter(token)
                || token.equals("(") || token.equals("{") || token.equals("[")
                || token.equals("\\pi") || token.equals("\\frac") || token.equals("\\sqrt")
                || token.equals("\\log") || token.equals("\\ln")
                || token.equals("\\sin") || token.equals("\\cos") || token.equals("\\tan");
    }

    private boolean isNumber(String token) {
        char first = token.charAt(0);
        return (first >= '0' && first <= '9') || first == '.';
    }

    private String peek() {
        if (position < tokens.size()) {
            return tokens.get(position);
        }
        return null;
    }

    private boolean peekIs(String expected) {
        return expected.equals(peek());
    }

    private String next() {
        if (position >= tokens.size()) {
            throw new MathSyntaxException("The expression ends too early.");
        }
        String token = tokens.get(position);
        position++;
        return token;
    }

    private void expect(String expected) {
        if (!peekIs(expected)) {
            throw new MathSyntaxException("Expected \"" + expected + "\".");
        }
        next();
    }
}
