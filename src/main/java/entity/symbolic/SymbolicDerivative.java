package entity.symbolic;

import entity.Context;
import entity.MathSyntaxException;
import entity.UncalculableException;
import entity.UncalculableReason;
import entity.UndefinedException;
import entity.UserFunction;
import entity.calculus.DerivativeCalculator;
import entity.node.BinaryNode;
import entity.node.CallNode;
import entity.node.DerivativeNode;
import entity.node.FunctionNode;
import entity.node.LimitNode;
import entity.node.LiteralNode;
import entity.node.Node;
import entity.node.NodeVisitor;
import entity.node.NumberNode;
import entity.node.PlusMinusNode;
import entity.node.QuantifierNode;
import entity.node.RelationNode;
import entity.node.SeriesNode;
import entity.node.SetLiteralNode;
import entity.node.UnaryNode;
import entity.node.VariableNode;
import entity.node.VectorNode;
import entity.value.NumberValue;
import entity.value.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// d/dx written out: parser nodes -> Expr, each derivative swapped for its formula
public final class SymbolicDerivative {
    private static final int MAX_CALL_DEPTH = 32;
    private static final double MAX_EXACT = 1e15;
    private static final long DECIMAL_SCALE = 1_000_000_000L;

    private final Map<String, UserFunction> functions;
    private final NodeVisitor<Expr> outsideDerivative = new Converter(false);
    private final NodeVisitor<Expr> insideDerivative = new Converter(true);
    private int callDepth;

    private SymbolicDerivative(Map<String, UserFunction> functions) {
        this.functions = functions;
    }

    public static Expr differentiate(Node node, Map<String, UserFunction> functions) {
        try {
            return Simplifier.simplify(new SymbolicDerivative(functions).convert(node, false));
        } catch (ArithmeticException e) {
            throw new UncalculableException(UncalculableReason.TOO_MUCH_WORK);  // rational overflowed
        }
    }

    // ---- nodes -> Expr

    private Expr convert(Node node, boolean inDerivative) {
        return node.accept(inDerivative ? insideDerivative : outsideDerivative);
    }

    private final class Converter implements NodeVisitor<Expr> {
        private final boolean inDerivative;

        private Converter(boolean inDerivative) {
            this.inDerivative = inDerivative;
        }

        @Override
        public Expr visitNumber(NumberNode node) {
            return number(node.getValue(), node.getDecimals());
        }

        @Override
        public Expr visitVariable(VariableNode node) {
            return Expr.var(node.getName());
        }

        @Override
        public Expr visitLiteral(LiteralNode node) {
            if (node.getValue() instanceof NumberValue) {
                double v = ((NumberValue) node.getValue()).getValue();
                if (v == Math.E) {
                    return Expr.constE();
                }
                if (v == Math.PI) {
                    return Expr.constPi();
                }
            }
            return opaque(node, inDerivative);
        }

        @Override
        public Expr visitBinary(BinaryNode node) {
            Expr e = binary(node, inDerivative);
            return e != null ? e : opaque(node, inDerivative);
        }

        @Override
        public Expr visitUnary(UnaryNode node) {
            if (node.getOp() == UnaryNode.Op.NEGATE) {
                return Expr.mul(Expr.num(-1), convert(node.getOperand(), inDerivative));
            }
            return opaque(node, inDerivative);
        }

        @Override
        public Expr visitFunction(FunctionNode node) {
            Expr e = function(node, inDerivative);
            return e != null ? e : opaque(node, inDerivative);
        }

        @Override
        public Expr visitCall(CallNode node) {
            return call(node, inDerivative);
        }

        @Override
        public Expr visitDerivative(DerivativeNode node) {
            return derivative(node);
        }

        @Override
        public Expr visitPlusMinus(PlusMinusNode node) {
            throw new UncalculableException(UncalculableReason.NOT_DIFFERENTIABLE);
        }

        @Override
        public Expr visitLimit(LimitNode node) {
            return opaque(node, inDerivative);
        }

        @Override
        public Expr visitQuantifier(QuantifierNode node) {
            return opaque(node, inDerivative);
        }

        @Override
        public Expr visitRelation(RelationNode node) {
            return opaque(node, inDerivative);
        }

        @Override
        public Expr visitSeries(SeriesNode node) {
            return opaque(node, inDerivative);
        }

        @Override
        public Expr visitSetLiteral(SetLiteralNode node) {
            return opaque(node, inDerivative);
        }

        @Override
        public Expr visitVector(VectorNode node) {
            return opaque(node, inDerivative);
        }
    }

    private static Expr number(double value, int decimals) {
        if (Double.isNaN(value) || Double.isInfinite(value) || Math.abs(value) > MAX_EXACT) {
            throw new UncalculableException(UncalculableReason.TOO_BIG);
        }
        if (decimals > 0) {
            long scale = 1;
            for (int i = 0; i < Math.min(decimals, 9); i++) {
                scale *= 10;
            }
            return Expr.num(Rational.of(Math.round(value * scale), scale), true);
        }
        if (value == Math.rint(value)) {
            return Expr.num(Rational.of((long) value), false);
        }
        return Expr.num(Rational.of(Math.round(value * DECIMAL_SCALE), DECIMAL_SCALE), true);
    }

    private Expr binary(BinaryNode b, boolean inDerivative) {
        switch (b.getOp()) {
            case ADD:
                return Expr.add(convert(b.getLeft(), inDerivative), convert(b.getRight(), inDerivative));
            case SUBTRACT:
                return Expr.add(convert(b.getLeft(), inDerivative),
                        Expr.mul(Expr.num(-1), convert(b.getRight(), inDerivative)));
            case MULTIPLY:
                return Expr.mul(convert(b.getLeft(), inDerivative), convert(b.getRight(), inDerivative));
            case DIVIDE:
                return Expr.mul(convert(b.getLeft(), inDerivative),
                        Expr.pow(convert(b.getRight(), inDerivative), Expr.num(-1)));
            case POWER:
                return Expr.pow(convert(b.getLeft(), inDerivative), convert(b.getRight(), inDerivative));
            default:
                return null;
        }
    }

    private Expr function(FunctionNode f, boolean inDerivative) {
        Expr arg = convert(f.getArgument(), inDerivative);
        Expr extra = f.getExtra() == null ? null : convert(f.getExtra(), inDerivative);
        switch (f.getName()) {
            case "\\sin":
            case "\\cos":
            case "\\tan":
            case "\\ln":
                return Expr.fn(f.getName().substring(1), arg);
            case "\\log":
                return Expr.log(arg, extra);
            case "\\sqrt":
                Expr degree = extra == null ? Expr.num(2) : extra;
                return Expr.pow(arg, Expr.pow(degree, Expr.num(-1)));
            default:
                return null;
        }
    }

    // f(u): body w/ params swapped for the args; f'(u) = f' at u, no chain factor
    private Expr call(CallNode c, boolean inDerivative) {
        UserFunction f = functions.get(c.getName());
        if (f == null) {
            throw new MathSyntaxException("Unknown variable \"" + c.getName() + "\".");
        }
        List<String> parameters = f.getParameters();
        if (parameters.size() != c.getArguments().size()) {
            String inputs = parameters.size() == 1 ? " input." : " inputs.";
            throw new MathSyntaxException(c.getName() + " needs " + parameters.size() + inputs);
        }
        int order = c.getDerivativeOrder();
        if (order > DerivativeCalculator.MAX_ORDER) {
            throw new UncalculableException(UncalculableReason.MAX_DERIVATIVE);
        }
        if (c.getDerivativeParameter() < 0 || c.getDerivativeParameter() >= parameters.size()) {
            throw new UncalculableException(UncalculableReason.NOT_AN_INPUT);
        }
        if (++callDepth > MAX_CALL_DEPTH) {
            throw new UncalculableException(UncalculableReason.TOO_MUCH_WORK);
        }
        try {
            Expr body = convert(f.getBody(), inDerivative || order > 0);
            if (order > 0) {
                body = nth(body, parameters.get(c.getDerivativeParameter()), order, false);
            }
            Map<String, Expr> values = new HashMap<>();
            for (int i = 0; i < parameters.size(); i++) {
                values.put(parameters.get(i), convert(c.getArguments().get(i), inDerivative));
            }
            return substitute(body, values);
        } finally {
            callDepth--;
        }
    }

    private Expr derivative(DerivativeNode node) {
        return nth(convert(node.getBody(), true), node.getVariable(), node.getOrder(), !node.isPartial());
    }

    // no letters -> just its value (e.g. a sum); else can't do it
    private Expr opaque(Node node, boolean inDerivative) {
        Set<String> free = new HashSet<>();
        node.collectFreeVariables(free);
        if (free.isEmpty()) {
            Value v = node.evaluate(new Context(functions));
            if (!(v instanceof NumberValue)) {
                throw new UncalculableException(UncalculableReason.NOT_DIFFERENTIABLE);
            }
            double value = ((NumberValue) v).getValue();
            if (Double.isNaN(value)) {
                throw new UndefinedException();
            }
            return number(value, 0);
        }
        if (inDerivative && node instanceof UnaryNode) {
            UnaryNode.Op op = ((UnaryNode) node).getOp();
            if (op == UnaryNode.Op.ABSOLUTE || op == UnaryNode.Op.NORM) {
                throw new UncalculableException(UncalculableReason.ABS_DERIVATIVE);
            }
            if (op == UnaryNode.Op.FACTORIAL) {
                throw new UncalculableException(UncalculableReason.FACTORIAL_DERIVATIVE);
            }
        }
        throw new UncalculableException(UncalculableReason.NOT_DIFFERENTIABLE);
    }

    // ---- the rules

    private static Expr nth(Expr u, String x, int order, boolean implicit) {
        if (order > DerivativeCalculator.MAX_ORDER) {
            throw new UncalculableException(UncalculableReason.MAX_DERIVATIVE);
        }
        Expr result = Simplifier.simplify(u);
        for (int i = 0; i < order; i++) {
            result = Simplifier.together(Simplifier.simplify(d(result, x, implicit)));
        }
        return result;
    }

    // implicit: other letters are y(x), partial: they're constants
    private static Expr d(Expr u, String x, boolean implicit) {
        switch (u.kind()) {
            case NUM:
            case E:
            case PI:
                return Expr.num(0);
            case VAR:
                if (u.name().equals(x)) {
                    return Expr.num(1);
                }
                return implicit ? Expr.dvar(u.name(), x) : Expr.num(0);
            case ADD:
                List<Expr> terms = new ArrayList<>();
                for (Expr t : u.terms()) {
                    terms.add(d(t, x, implicit));
                }
                return Expr.add(terms);
            case MUL:
                return productRule(u.terms(), x, implicit);
            case POW:
                return powerRule(u.base(), u.exponent(), x, implicit);
            case FN:
                return functionRule(u.name(), u.arg(), x, implicit);
            case LOG:
                return logRule(u.arg(), u.logBase(), x, implicit);
            default:
                throw new UncalculableException(UncalculableReason.NOT_DIFFERENTIABLE);  // dy/dx again
        }
    }

    private static Expr productRule(List<Expr> factors, String x, boolean implicit) {
        List<Expr> terms = new ArrayList<>();
        for (int i = 0; i < factors.size(); i++) {
            if (!depends(factors.get(i), x, implicit)) {
                continue;
            }
            List<Expr> term = new ArrayList<>(factors);
            term.set(i, d(factors.get(i), x, implicit));
            terms.add(Expr.mul(term));
        }
        return terms.isEmpty() ? Expr.num(0) : Expr.add(terms);
    }

    private static Expr powerRule(Expr base, Expr exp, String x, boolean implicit) {
        boolean baseVaries = depends(base, x, implicit);
        boolean expVaries = depends(exp, x, implicit);
        if (!expVaries) {
            return Expr.mul(exp, Expr.pow(base, Expr.add(exp, Expr.num(-1))), d(base, x, implicit));
        }
        Expr whole = Expr.pow(base, exp);
        if (!baseVaries) {
            return Expr.mul(whole, Expr.fn("ln", base), d(exp, x, implicit));
        }
        // u^v (v' ln u + v u'/u)
        return Expr.mul(whole, Expr.add(
                Expr.mul(d(exp, x, implicit), Expr.fn("ln", base)),
                Expr.mul(exp, d(base, x, implicit), Expr.pow(base, Expr.num(-1)))));
    }

    private static Expr functionRule(String name, Expr arg, String x, boolean implicit) {
        Expr inner = d(arg, x, implicit);
        switch (name) {
            case "sin":
                return Expr.mul(Expr.fn("cos", arg), inner);
            case "cos":
                return Expr.mul(Expr.num(-1), Expr.fn("sin", arg), inner);
            case "tan":
                return Expr.mul(Expr.pow(Expr.fn("cos", arg), Expr.num(-2)), inner);
            case "ln":
                return Expr.mul(inner, Expr.pow(arg, Expr.num(-1)));
            default:
                throw new UncalculableException(UncalculableReason.NOT_DIFFERENTIABLE);
        }
    }

    private static Expr logRule(Expr arg, Expr base, String x, boolean implicit) {
        Expr b = base == null ? Expr.num(10) : base;
        Expr asLn = Expr.mul(Expr.fn("ln", arg), Expr.pow(Expr.fn("ln", b), Expr.num(-1)));
        if (depends(b, x, implicit)) {
            return d(asLn, x, implicit);
        }
        return Expr.mul(d(arg, x, implicit), Expr.pow(arg, Expr.num(-1)), Expr.pow(Expr.fn("ln", b), Expr.num(-1)));
    }

    private static boolean depends(Expr e, String x, boolean implicit) {
        switch (e.kind()) {
            case NUM:
            case E:
            case PI:
                return false;
            case VAR:
                return implicit || e.name().equals(x);
            case DVAR:
                return true;
            default:
                for (Expr child : children(e)) {
                    if (depends(child, x, implicit)) {
                        return true;
                    }
                }
                return false;
        }
    }

    private static List<Expr> children(Expr e) {
        List<Expr> out = new ArrayList<>();
        if (e.terms() != null && e.kind() != Expr.Kind.DVAR) {
            out.addAll(e.terms());
        }
        if (e.base() != null) {
            out.add(e.base());
        }
        if (e.exponent() != null) {
            out.add(e.exponent());
        }
        return out;
    }

    private static Expr substitute(Expr e, Map<String, Expr> values) {
        switch (e.kind()) {
            case VAR:
                return values.getOrDefault(e.name(), e);
            case ADD:
            case MUL:
                List<Expr> parts = new ArrayList<>();
                for (Expr t : e.terms()) {
                    parts.add(substitute(t, values));
                }
                return e.kind() == Expr.Kind.ADD ? Expr.add(parts) : Expr.mul(parts);
            case POW:
                return Expr.pow(substitute(e.base(), values), substitute(e.exponent(), values));
            case FN:
                return Expr.fn(e.name(), substitute(e.arg(), values));
            case LOG:
                return Expr.log(substitute(e.arg(), values),
                        e.logBase() == null ? null : substitute(e.logBase(), values));
            default:
                return e;
        }
    }
}
