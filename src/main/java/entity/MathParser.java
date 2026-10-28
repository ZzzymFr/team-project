package entity;

import entity.calculus.DerivativeCalculator;
import entity.node.BinaryNode;
import entity.node.CallNode;
import entity.node.DerivativeNode;
import entity.node.FunctionNode;
import entity.node.LimitNode;
import entity.node.LiteralNode;
import entity.node.Node;
import entity.node.NumberNode;
import entity.node.PlusMinusNode;
import entity.node.QuantifierNode;
import entity.node.RelationNode;
import entity.node.SeriesNode;
import entity.node.SetLiteralNode;
import entity.node.UnaryNode;
import entity.node.VectorNode;
import entity.node.VariableNode;
import entity.value.BooleanValue;
import entity.value.NumberSetValue;
import entity.value.NumberValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class MathParser {
    // stuff we cant calc (integrals, ..., maps)
    private static final List<String> MEANINGLESS = Arrays.asList("\\int", "\\ldots", "\\rightarrow",
            "\\partial", ":", ";");
    // tokens that cant start a value
    private static final List<String> OPERATORS = Arrays.asList("\\leq", "\\geq", "\\neq", "\\approx",
            "\\times", "\\cdot", "\\land", "\\lor", "\\oplus", "\\Rightarrow", "\\Leftrightarrow",
            "\\leftrightarrow", "\\in", "\\notin", "\\subset", "\\cup", "\\cap", "\\perp", "\\rangle",
            "\\}");

    private final List<String> tokens;
    private final Map<String, UserFunction> functions;
    private int position = 0;
    private int plusMinusCount = 0;
    private final List<String> boundNames = new ArrayList<>();

    public MathParser(List<String> tokens, Map<String, UserFunction> functions) {
        this.tokens = tokens;
        this.functions = functions;
    }

    public Node parse() {
        if (tokens.isEmpty()) {
            throw new MathSyntaxException("The expression is empty.");
        }
        try {
            Node result = parseStatement();
            if (position < tokens.size()) {
                throw new MathSyntaxException("Unexpected \"" + peek() + "\".");
            }
            return result;
        } catch (UncalculableException e) {
            throw e;
        } catch (MathSyntaxException e) {
            // leftover : or ; is just prose
            if (tokens.contains(":") || tokens.contains(";")) {
                throw new UncalculableException();
            }
            throw e;
        }
    }

    public int getPlusMinusCount() {
        return plusMinusCount;
    }

    private Node parseStatement() {
        return parseIff();
    }

    private Node parseIff() {
        Node result = parseImplies();
        while (peekIs("\\Leftrightarrow") || peekIs("\\leftrightarrow")) {
            next();
            result = new BinaryNode(BinaryNode.Op.IFF, result, parseImplies());
        }
        return result;
    }

    private Node parseImplies() {
        Node left = parseOr();
        if (peekIs("\\Rightarrow") || peekIs("\\rightarrow")) {
            next();
            return new BinaryNode(BinaryNode.Op.IMPLIES, left, parseImplies());
        }
        return left;
    }

    private Node parseOr() {
        Node result = parseAnd();
        while (peekIs("\\lor") || peekIs("\\oplus")) {
            if (next().equals("\\lor")) {
                result = new BinaryNode(BinaryNode.Op.OR, result, parseAnd());
            } else {
                result = new BinaryNode(BinaryNode.Op.XOR, result, parseAnd());
            }
        }
        return result;
    }

    private Node parseAnd() {
        Node result = parseNot();
        while (peekIs("\\land")) {
            next();
            result = new BinaryNode(BinaryNode.Op.AND, result, parseNot());
        }
        return result;
    }

    private Node parseNot() {
        if (peekIs("\\neg")) {
            next();
            return new UnaryNode(UnaryNode.Op.NOT, parseNot());
        }
        if (peekIs("\\forall") || peekIs("\\exists")) {
            return parseQuantifier();
        }
        return parseRelation();
    }

    // forall/exists name [\in dom] , body. no dom = R
    private Node parseQuantifier() {
        boolean isForAll = next().equals("\\forall");
        int plusMinusBefore = plusMinusCount;
        String letter = next();
        if (!MathLexer.isLetter(letter)) {
            throw new MathSyntaxException("Unexpected \"" + letter + "\".");
        }
        String variable = parseName(letter);
        Node domain = null;
        if (peekIs("\\in")) {
            next();
            domain = parsePrimary();
            while (peekIs("\\cup") || peekIs("\\cap")) {
                if (next().equals("\\cup")) {
                    domain = new BinaryNode(BinaryNode.Op.UNION, domain, parsePrimary());
                } else {
                    domain = new BinaryNode(BinaryNode.Op.INTERSECT, domain, parsePrimary());
                }
            }
        }
        if (peekIs(",") || peekIs(":")) {
            next();
        }
        if (peek() == null || peekIs(")") || peekIs("}") || peekIs("]") || peekIs("\\}")) {
            throw new UncalculableException();
        }
        boundNames.add(variable);
        Node body = parseStatement();
        boundNames.remove(boundNames.size() - 1);
        if (plusMinusCount > plusMinusBefore) {
            throw new UncalculableException();
        }
        return new QuantifierNode(isForAll, variable, domain, body);
    }

    private Node parseRelation() {
        Node first = parseSetExpression();
        if (!isRelationOperator(peek())) {
            return first;
        }
        List<Node> operands = new ArrayList<>();
        List<String> operators = new ArrayList<>();
        operands.add(first);
        while (isRelationOperator(peek())) {
            operators.add(next());
            operands.add(parseSetExpression());
        }
        return new RelationNode(operands, operators);
    }

    private Node parseSetExpression() {
        Node result = parseAdditive();
        while (peekIs("\\cup") || peekIs("\\cap")) {
            if (next().equals("\\cup")) {
                result = new BinaryNode(BinaryNode.Op.UNION, result, parseAdditive());
            } else {
                result = new BinaryNode(BinaryNode.Op.INTERSECT, result, parseAdditive());
            }
        }
        return result;
    }

    private Node parseAdditive() {
        Node result = parseTerm();
        while (peekIs("+") || peekIs("-") || peekIs("\\pm")) {
            String operator = next();
            if (operator.equals("\\pm")) {
                int signIndex = plusMinusCount;
                plusMinusCount++;
                result = new PlusMinusNode(signIndex, result, parseTerm());
                continue;
            }
            Node right = parseTerm();
            if (operator.equals("+")) {
                result = new BinaryNode(BinaryNode.Op.ADD, result, right);
            } else {
                result = new BinaryNode(BinaryNode.Op.SUBTRACT, result, right);
            }
        }
        return result;
    }

    private Node parseTerm() {
        Node result = parseUnary();
        while (true) {
            if (peekIs("*")) {
                next();
                result = new BinaryNode(BinaryNode.Op.MULTIPLY, result, parseUnary());
            } else if (peekIs("\\cdot")) {
                next();
                result = new BinaryNode(BinaryNode.Op.DOT, result, parseUnary());
            } else if (peekIs("\\times")) {
                next();
                result = new BinaryNode(BinaryNode.Op.CROSS, result, parseUnary());
            } else if (peekIs("/")) {
                next();
                result = new BinaryNode(BinaryNode.Op.DIVIDE, result, parseUnary());
            } else if (startsValue(peek())) {
                result = new BinaryNode(BinaryNode.Op.MULTIPLY, result, parsePower());
            } else {
                return result;
            }
        }
    }

    private Node parseUnary() {
        if (peekIs("-")) {
            next();
            return new UnaryNode(UnaryNode.Op.NEGATE, parseUnary());
        }
        if (peekIs("+")) {
            next();
            return parseUnary();
        }
        if (peekIs("\\pm")) {
            next();
            int signIndex = plusMinusCount;
            plusMinusCount++;
            return new PlusMinusNode(signIndex, null, parseUnary());
        }
        return parsePower();
    }

    private Node parsePower() {
        Node base = parsePostfix();
        if (peekIs("^")) {
            next();
            return new BinaryNode(BinaryNode.Op.POWER, base, parseUnary());
        }
        return base;
    }

    private Node parsePostfix() {
        Node value = parsePrimary();
        while (peekIs("!") || peekIs("%")) {
            if (next().equals("!")) {
                value = new UnaryNode(UnaryNode.Op.FACTORIAL, value);
            } else {
                value = new UnaryNode(UnaryNode.Op.PERCENT, value);
            }
        }
        return value;
    }

    private Node parsePrimary() {
        String token = next();

        if (isNumber(token)) {
            return parseNumber(token);
        }
        if (token.equals("(")) {
            return parseParenthesized();
        }
        if (token.equals("\\langle")) {
            return parseAngleVector();
        }
        if (token.equals("\\|")) {
            return new UnaryNode(UnaryNode.Op.NORM, parseRestOfGroup("\\|"));
        }
        if (token.equals("{")) {
            return parseRestOfGroup("}");
        }
        if (token.equals("[")) {
            return parseRestOfGroup("]");
        }
        if (token.equals("|")) {
            return new UnaryNode(UnaryNode.Op.ABSOLUTE, parseRestOfGroup("|"));
        }
        if (token.equals("\\{")) {
            return parseSetLiteral();
        }
        if (token.equals("\\mathbb")) {
            return parseNumberSet();
        }
        if (token.equals("\\pi")) {
            return new LiteralNode(new NumberValue(Math.PI), false);
        }
        if (token.equals("e") && !boundNames.contains("e")) {
            return new LiteralNode(new NumberValue(Math.E), false);
        }
        if (token.equals("\\frac")) {
            Node derivative = parseDerivative();
            if (derivative != null) {
                return derivative;
            }
            expect("{");
            Node top = parseRestOfGroup("}");
            expect("{");
            Node bottom = parseRestOfGroup("}");
            return new BinaryNode(BinaryNode.Op.DIVIDE, top, bottom);
        }
        if (token.equals("\\sqrt")) {
            return parseSqrt();
        }
        if (token.equals("\\lim")) {
            return parseLimit();
        }
        if (token.equals("\\sum") || token.equals("\\prod")) {
            return parseSeries(token.equals("\\prod"));
        }
        if (token.equals("\\log")) {
            return parseLog();
        }
        if (token.equals("\\sin") || token.equals("\\cos") || token.equals("\\tan") || token.equals("\\ln")) {
            Node power = parseFunctionPower();
            Node value = new FunctionNode(token, parseFunctionArgument(), null);
            return power == null ? value : new BinaryNode(BinaryNode.Op.POWER, value, power);
        }
        if (token.equals("\\infty")) {
            return new LiteralNode(new NumberValue(Double.POSITIVE_INFINITY), true);
        }
        if (token.equals("\\top")) {
            return new LiteralNode(BooleanValue.of(true), false);
        }
        if (token.equals("\\bot")) {
            return new LiteralNode(BooleanValue.of(false), false);
        }
        if (token.equals("\\vec") || token.equals("\\hat") || token.equals("\\bar")
                || token.equals("\\overline")) {
            return parseDecorated(token);
        }
        if (MathLexer.isLetter(token)) {
            if (functions.containsKey(token) && !peekIs("_")) {
                return parseFunctionUse(token);
            }
            String name = parseName(token);
            if (peekIs("'")) {
                throw new UncalculableException();
            }
            return new VariableNode(name);
        }
        if (MEANINGLESS.contains(token)) {
            throw new UncalculableException();
        }
        if (OPERATORS.contains(token)) {
            throw new MathSyntaxException("Unexpected \"" + token + "\".");
        }
        if (token.startsWith("\\") && token.length() > 1) {
            throw new MathSyntaxException("\"" + token + "\" is not supported.");
        }
        throw new MathSyntaxException("Unexpected \"" + token + "\".");
    }

    private Node parseParenthesized() {
        Node first = parseStatement();
        if (!peekIs(",")) {
            expect(")");
            return first;
        }
        List<Node> components = new ArrayList<>();
        components.add(first);
        while (peekIs(",")) {
            next();
            components.add(parseStatement());
        }
        expect(")");
        return new VectorNode(components);
    }

    private Node parseAngleVector() {
        List<Node> components = new ArrayList<>();
        components.add(parseStatement());
        expect(",");
        components.add(parseStatement());
        while (peekIs(",")) {
            next();
            components.add(parseStatement());
        }
        expect("\\rangle");
        if (components.size() == 2 && components.get(0) instanceof VectorNode
                && components.get(1) instanceof VectorNode) {
            return new BinaryNode(BinaryNode.Op.DOT, components.get(0), components.get(1));
        }
        return new VectorNode(components);
    }

    private Node parseSetLiteral() {
        List<Node> elements = new ArrayList<>();
        if (peekIs("\\}")) {
            next();
            return new SetLiteralNode(elements);
        }
        elements.add(parseStatement());
        while (peekIs(",")) {
            next();
            elements.add(parseStatement());
        }
        expect("\\}");
        return new SetLiteralNode(elements);
    }

    private Node parseNumberSet() {
        expect("{");
        String letter = next();
        expect("}");
        NumberSetValue.Kind kind;
        switch (letter) {
            case "N":
                kind = NumberSetValue.Kind.NATURAL;
                break;
            case "Z":
                kind = NumberSetValue.Kind.INTEGER;
                break;
            case "Q":
                kind = NumberSetValue.Kind.RATIONAL;
                break;
            case "R":
                kind = NumberSetValue.Kind.REAL;
                break;
            case "C":
                kind = NumberSetValue.Kind.COMPLEX;
                break;
            default:
                throw new UncalculableException();
        }
        int dimension = 1;
        if (peekIs("^")) {
            next();
            boolean braced = peekIs("{");
            if (braced) {
                next();
            }
            dimension = parseDimension(next());
            if (braced) {
                expect("}");
            }
        }
        return new LiteralNode(new NumberSetValue(kind, dimension), false);
    }

    private int parseDimension(String token) {
        if (MathLexer.isLetter(token)) {
            throw new UncalculableException();
        }
        try {
            int dimension = Integer.parseInt(token);
            if (dimension >= 1) {
                return dimension;
            }
        } catch (NumberFormatException e) {
            // reported below
        }
        throw new MathSyntaxException("The dimension of a number set must be a positive integer.");
    }

    private String parseName(String letter) {
        if (!peekIs("_")) {
            return letter;
        }
        next();
        List<String> subscript = new ArrayList<>();
        if (peekIs("{")) {
            next();
            int depth = 1;
            while (true) {
                String token = next();
                if (token.equals("{")) {
                    depth++;
                } else if (token.equals("}")) {
                    depth--;
                    if (depth == 0) {
                        break;
                    }
                }
                subscript.add(token);
            }
        } else {
            subscript.add(next());
        }
        if (subscript.isEmpty()) {
            throw new MathSyntaxException("The subscript of \"" + letter + "\" is empty.");
        }
        if (subscript.size() == 1) {
            return letter + "_" + subscript.get(0);
        }
        return letter + "_{" + String.join("", subscript) + "}";
    }

    private Node parseDecorated(String command) {
        if (peekIs("{") && position + 1 < tokens.size() && MathLexer.isLetter(tokens.get(position + 1))) {
            int start = position;
            next();
            String name = parseName(next());
            if (peekIs("}")) {
                next();
                return new VariableNode(command + "{" + name + "}");
            }
            position = start;
        }
        Node argument = parseFunctionArgument();
        if (command.equals("\\vec")) {
            return argument;
        }
        if (command.equals("\\hat")) {
            return new UnaryNode(UnaryNode.Op.UNIT, argument);
        }
        throw new UncalculableException();
    }

    private Node parseFunctionUse(String name) {
        int order = 0;
        while (peekIs("'")) {
            next();
            order++;
        }
        if (order == 0 && peekIs("^") && "{".equals(tokenAt(position + 1)) && "(".equals(tokenAt(position + 2))
                && orderOf(tokenAt(position + 3)) >= 0 && ")".equals(tokenAt(position + 4))
                && "}".equals(tokenAt(position + 5))) {
            order = orderOf(tokenAt(position + 3));
            position = position + 6;
        }
        if (order > DerivativeCalculator.MAX_ORDER) {
            throw new UncalculableException();
        }
        return parseCall(name, order, 0);
    }

    private Node parseCall(String name, int derivativeOrder, int derivativeParameter) {
        if (!peekIs("(")) {
            throw new MathSyntaxException("Write " + name + "(...) to use the function " + name + ".");
        }
        next();
        List<Node> arguments = new ArrayList<>();
        arguments.add(parseStatement());
        while (peekIs(",")) {
            next();
            arguments.add(parseStatement());
        }
        expect(")");
        return new CallNode(name, arguments, derivativeOrder, derivativeParameter, functions);
    }

    // parse \frac{d}{dx} stuff, null if its not a derivative
    private Node parseDerivative() {
        int start = position;
        String operator = tokenAt(position + 1);
        if (!"{".equals(peek()) || !("d".equals(operator) || "\\partial".equals(operator))) {
            return null;
        }
        position = position + 2;
        int order = readDerivativeOrder();
        String function = null;
        if (peek() != null && MathLexer.isLetter(peek()) && functions.containsKey(peek())) {
            function = next();
        }
        String variable = null;
        if (peekIs("}") && "{".equals(tokenAt(position + 1)) && operator.equals(tokenAt(position + 2))) {
            position = position + 3;
            if (peek() != null && MathLexer.isLetter(peek())) {
                variable = next();
            }
        }
        if (variable == null || order != readDerivativeOrder() || !peekIs("}")) {
            position = start;
            return null;
        }
        next();
        if (order < 1 || order > DerivativeCalculator.MAX_ORDER) {
            throw new UncalculableException();
        }
        if (function == null) {
            return new DerivativeNode(variable, order, parseBody(variable));
        }
        int parameter = functions.get(function).getParameters().indexOf(variable);
        if (parameter < 0) {
            throw new UncalculableException();
        }
        return parseCall(function, order, parameter);
    }

    // \lim_{x\to a}, ^+ / ^- makes it one sided
    private Node parseLimit() {
        expect("_");
        expect("{");
        String letter = next();
        if (!MathLexer.isLetter(letter)) {
            throw new MathSyntaxException("Unexpected \"" + letter + "\".");
        }
        String variable = parseName(letter);
        expect("\\rightarrow");
        int start = position;
        int end = closingBrace(start);
        int targetEnd = end;
        int side = 0;
        if (endsWith(start, end, "^", "{", "+", "}")) {
            side = 1;
        } else if (endsWith(start, end, "^", "{", "-", "}")) {
            side = -1;
        } else if (endsWith(start, end, "^", "+")) {
            side = 1;
        } else if (endsWith(start, end, "^", "-")) {
            side = -1;
        }
        if (side != 0) {
            targetEnd = tokens.get(end - 1).equals("}") ? end - 4 : end - 2;
        }
        Node target = parseBound(start, targetEnd);
        position = end + 1;
        return new LimitNode(variable, target, side, parseBody(variable));
    }

    // \sum / \prod w/ range, no range -> uncalc
    private Node parseSeries(boolean isProduct) {
        if (!peekIs("_") || !"{".equals(tokenAt(position + 1))) {
            throw new UncalculableException();
        }
        int start = position + 2;
        int end = closingBrace(start);
        if (start == end || !MathLexer.isLetter(tokens.get(start))) {
            throw new UncalculableException();
        }
        position = start + 1;
        String variable = parseName(tokens.get(start));
        if (position >= end || !tokens.get(position).equals("=")) {
            throw new UncalculableException();
        }
        Node lower = parseBound(position + 1, end);
        position = end + 1;
        if (!peekIs("^")) {
            throw new UncalculableException();
        }
        next();
        Node upper;
        if (peekIs("{")) {
            int upperEnd = closingBrace(position + 1);
            upper = parseBound(position + 1, upperEnd);
            position = upperEnd + 1;
        } else {
            next();
            upper = parseBound(position - 1, position);
        }
        return new SeriesNode(isProduct, variable, lower, upper, parseBody(variable));
    }

    // body where the var is bound (even e)
    private Node parseBody(String variable) {
        boundNames.add(variable);
        Node body = parseAdditive();
        boundNames.remove(boundNames.size() - 1);
        return body;
    }

    // bound inside the brackets, parsed on its own
    private Node parseBound(int from, int to) {
        if (from == to) {
            throw new MathSyntaxException("Unexpected \"" + tokens.get(to) + "\".");
        }
        MathParser boundParser = new MathParser(tokens.subList(from, to), functions);
        boundParser.boundNames.addAll(boundNames);
        Node bound = boundParser.parse();
        if (boundParser.getPlusMinusCount() > 0) {
            throw new UncalculableException();
        }
        return bound;
    }

    private int closingBrace(int start) {
        int depth = 1;
        for (int i = start; i < tokens.size(); i++) {
            if (tokens.get(i).equals("{")) {
                depth++;
            } else if (tokens.get(i).equals("}")) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        throw new MathSyntaxException("Expected \"}\".");
    }

    private boolean endsWith(int start, int end, String... suffix) {
        if (end - start < suffix.length) {
            return false;
        }
        for (int i = 0; i < suffix.length; i++) {
            if (!tokens.get(end - suffix.length + i).equals(suffix[i])) {
                return false;
            }
        }
        return true;
    }

    private int readDerivativeOrder() {
        if (peekIs("^") && orderOf(tokenAt(position + 1)) >= 0) {
            next();
            return orderOf(next());
        }
        if (peekIs("^") && "{".equals(tokenAt(position + 1)) && orderOf(tokenAt(position + 2)) >= 0
                && "}".equals(tokenAt(position + 3))) {
            position = position + 4;
            return orderOf(tokenAt(position - 2));
        }
        return 1;
    }

    private int orderOf(String token) {
        if (token == null || token.isEmpty() || token.length() > 3) {
            return -1;
        }
        for (int i = 0; i < token.length(); i++) {
            if (token.charAt(i) < '0' || token.charAt(i) > '9') {
                return -1;
            }
        }
        return Integer.parseInt(token);
    }

    private Node parseSqrt() {
        Node degree = null;
        if (peekIs("[")) {
            next();
            degree = parseRestOfGroup("]");
        }
        return new FunctionNode("\\sqrt", parseFunctionArgument(), degree);
    }

    private Node parseLog() {
        Node base = null;
        if (peekIs("_")) {
            next();
            base = parsePrimary();
        }
        return new FunctionNode("\\log", parseFunctionArgument(), base);
    }

    // \sin^2 x = (\sin x)^2, \sin^{-1} is ambiguous
    private Node parseFunctionPower() {
        if (!peekIs("^")) {
            return null;
        }
        next();
        if (peekIs("-") || (peekIs("{") && "-".equals(tokenAt(position + 1)))) {
            throw new UncalculableException();
        }
        return parsePrimary();
    }

    private Node parseFunctionArgument() {
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

    private Node parseRestOfGroup(String closingBracket) {
        Node value = parseStatement();
        expect(closingBracket);
        return value;
    }

    private Node parseNumber(String token) {
        double value;
        try {
            value = Double.parseDouble(token);
        } catch (NumberFormatException e) {
            throw new MathSyntaxException("\"" + token + "\" is not a valid number.");
        }
        int point = token.indexOf('.');
        int decimals = point == -1 ? 0 : token.length() - point - 1;
        return new NumberNode(value, decimals);
    }

    private boolean isRelationOperator(String token) {
        return "=".equals(token) || "<".equals(token) || ">".equals(token)
                || "\\leq".equals(token) || "\\geq".equals(token)
                || "\\neq".equals(token) || "\\approx".equals(token)
                || "\\in".equals(token) || "\\perp".equals(token)
                || "\\notin".equals(token) || "\\subset".equals(token);
    }

    private boolean startsValue(String token) {
        if (token == null) {
            return false;
        }
        return isNumber(token) || MathLexer.isLetter(token)
                || token.equals("(") || token.equals("{") || token.equals("[")
                || token.equals("\\pi") || token.equals("\\frac") || token.equals("\\sqrt")
                || token.equals("\\log") || token.equals("\\ln") || token.equals("\\lim")
                || token.equals("\\sum") || token.equals("\\prod")
                || token.equals("\\infty") || token.equals("\\top") || token.equals("\\bot")
                || token.equals("\\vec") || token.equals("\\hat") || token.equals("\\bar")
                || token.equals("\\overline")
                || token.equals("\\sin") || token.equals("\\cos") || token.equals("\\tan");
    }

    private boolean isNumber(String token) {
        char first = token.charAt(0);
        return (first >= '0' && first <= '9') || first == '.';
    }

    private String tokenAt(int index) {
        if (index < tokens.size()) {
            return tokens.get(index);
        }
        return null;
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
