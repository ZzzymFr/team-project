package entity;

import entity.node.BinaryNode;
import entity.node.FunctionNode;
import entity.node.LiteralNode;
import entity.node.Node;
import entity.node.NumberNode;
import entity.node.RelationNode;
import entity.node.UnaryNode;
import entity.node.VariableNode;
import entity.value.NumberValue;

import java.util.ArrayList;
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
        return parseAdditive();
    }

    private Node parseAdditive() {
        Node result = parseTerm();
        while (peekIs("+") || peekIs("-")) {
            String operator = next();
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
        while (peekIs("!")) {
            next();
            value = new UnaryNode(UnaryNode.Op.FACTORIAL, value);
        }
        return value;
    }

    private Node parsePrimary() {
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
            return new UnaryNode(UnaryNode.Op.ABSOLUTE, parseRestOfGroup("|"));
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
        if (MathLexer.isLetter(token)) {
            return new VariableNode(token);
        }
        if (token.startsWith("\\") && token.length() > 1) {
            throw new MathSyntaxException("\"" + token + "\" is not supported.");
        }
        throw new MathSyntaxException("Unexpected \"" + token + "\".");
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
        return "=".equals(token);
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
