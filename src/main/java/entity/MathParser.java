package entity;

import entity.node.BinaryNode;
import entity.node.CallNode;
import entity.node.FunctionNode;
import entity.node.LiteralNode;
import entity.node.Node;
import entity.node.NumberNode;
import entity.node.PlusMinusNode;
import entity.node.RelationNode;
import entity.node.SetLiteralNode;
import entity.node.UnaryNode;
import entity.node.VectorNode;
import entity.node.VariableNode;
import entity.value.BooleanValue;
import entity.value.NumberSetValue;
import entity.value.NumberValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class MathParser {
    private final List<String> tokens;
    private final Map<String, UserFunction> functions;
    private int position = 0;
    private int plusMinusCount = 0;

    public MathParser(List<String> tokens, Map<String, UserFunction> functions) {
        this.tokens = tokens;
        this.functions = functions;
    }

    public Node parse() {
        if (tokens.isEmpty()) {
            throw new MathSyntaxException("The expression is empty.");
        }
        Node result = parseStatement();
        if (position < tokens.size()) {
            throw new MathSyntaxException("Unexpected \"" + peek() + "\".");
        }
        return result;
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
        return parseRelation();
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
        if (token.equals("e")) {
            return new LiteralNode(new NumberValue(Math.E), false);
        }
        if (token.equals("\\frac")) {
            expect("{");
            Node top = parseRestOfGroup("}");
            expect("{");
            Node bottom = parseRestOfGroup("}");
            return new BinaryNode(BinaryNode.Op.DIVIDE, top, bottom);
        }
        if (token.equals("\\sqrt")) {
            return parseSqrt();
        }
        if (token.equals("\\log")) {
            return parseLog();
        }
        if (token.equals("\\sin") || token.equals("\\cos") || token.equals("\\tan") || token.equals("\\ln")) {
            return new FunctionNode(token, parseFunctionArgument(), null);
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
                return parseCall(token);
            }
            return new VariableNode(parseName(token));
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
                throw new MathSyntaxException("\"\\mathbb{" + letter + "}\" is not supported.");
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

    private Node parseCall(String name) {
        if (!peekIs("(")) {
            throw new MathSyntaxException("Write " + name + "(...) to use the function " + name + ".");
        }
        next();
        Node argument = parseRestOfGroup(")");
        return new CallNode(name, Collections.singletonList(argument), 0, 0, functions);
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
                || token.equals("\\log") || token.equals("\\ln")
                || token.equals("\\infty") || token.equals("\\top") || token.equals("\\bot")
                || token.equals("\\vec") || token.equals("\\hat") || token.equals("\\bar")
                || token.equals("\\overline")
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
