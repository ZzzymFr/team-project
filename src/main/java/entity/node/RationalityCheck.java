package entity.node;

import entity.symbolic.Rational;
import entity.value.NumberValue;
import entity.value.Value;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

// is a formula rational, from its shape not its float value
final class RationalityCheck implements NodeVisitor<Rationality> {
    private static final RationalityCheck INSTANCE = new RationalityCheck();
    private static final int MAX_DECIMALS = 18;

    private RationalityCheck() {
    }

    // overflow anywhere = cant tell, unknown wins all the way up anyway
    static Rationality of(Node node) {
        try {
            return node.accept(INSTANCE);
        } catch (ArithmeticException e) {
            return Rationality.UNKNOWN;
        }
    }

    @Override
    public Rationality visitNumber(NumberNode node) {
        double value = node.getValue();
        int decimals = node.getDecimals();
        if (!Double.isFinite(value) || decimals < 0 || decimals > MAX_DECIMALS) {
            return Rationality.UNKNOWN;
        }
        BigInteger digits = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_EVEN).unscaledValue();
        if (digits.bitLength() >= Long.SIZE) {
            return Rationality.UNKNOWN;
        }
        long scale = 1;
        for (int i = 0; i < decimals; i++) {
            scale = scale * 10;
        }
        return Rationality.exact(Rational.of(digits.longValue(), scale));
    }

    @Override
    public Rationality visitLiteral(LiteralNode node) {
        Value value = node.getValue();
        if (!(value instanceof NumberValue)) {
            return Rationality.UNKNOWN;
        }
        double number = ((NumberValue) value).getValue();
        if (number == Math.PI || number == Math.E) {
            return Rationality.IRRATIONAL;
        }
        if (number == Math.rint(number) && Math.abs(number) < 0x1p63) {
            return Rationality.exact(Rational.of((long) number));
        }
        return Rationality.UNKNOWN;
    }

    @Override
    public Rationality visitUnary(UnaryNode node) {
        Rationality inner = node.getOperand().accept(this);
        if (!inner.isExact()) {
            boolean keepsIrrational = node.getOp() == UnaryNode.Op.NEGATE || node.getOp() == UnaryNode.Op.ABSOLUTE
                    || node.getOp() == UnaryNode.Op.PERCENT;
            return keepsIrrational ? inner : Rationality.UNKNOWN;
        }
        Rational value = inner.getValue();
        switch (node.getOp()) {
            case NEGATE:
                return Rationality.exact(value.neg());
            case ABSOLUTE:
                return Rationality.exact(value.isNegative() ? value.neg() : value);
            case PERCENT:
                return Rationality.exact(value.div(Rational.of(100)));
            case FACTORIAL:
                return factorial(value);
            default:
                return Rationality.UNKNOWN;
        }
    }

    private static Rationality factorial(Rational n) {
        if (!n.isInteger() || n.isNegative()) {
            return Rationality.UNKNOWN;
        }
        long result = 1;
        for (long i = 2; i <= n.getNum(); i++) {
            result = Math.multiplyExact(result, i);
        }
        return Rationality.exact(Rational.of(result));
    }

    @Override
    public Rationality visitBinary(BinaryNode node) {
        switch (node.getOp()) {
            case ADD:
            case SUBTRACT:
            case MULTIPLY:
            case DIVIDE:
                return arithmetic(node.getOp(), node.getLeft().accept(this), node.getRight().accept(this));
            case DOT:
            case CROSS:
                // \cdot \times on plain numbers just multiply, vectors come out unknown
                return arithmetic(BinaryNode.Op.MULTIPLY, node.getLeft().accept(this), node.getRight().accept(this));
            case POWER:
                return power(node.getLeft().accept(this), node.getRight().accept(this));
            default:
                return Rationality.UNKNOWN;
        }
    }

    private static Rationality arithmetic(BinaryNode.Op op, Rationality left, Rationality right) {
        if (left.isExact() && right.isExact()) {
            return Rationality.exact(exactly(op, left.getValue(), right.getValue()));
        }
        if (left.isExact() && right.isIrrational()) {
            return keepsIrrational(op, left.getValue()) ? Rationality.IRRATIONAL : Rationality.UNKNOWN;
        }
        if (left.isIrrational() && right.isExact()) {
            return keepsIrrational(op, right.getValue()) ? Rationality.IRRATIONAL : Rationality.UNKNOWN;
        }
        return Rationality.UNKNOWN;
    }

    private static Rational exactly(BinaryNode.Op op, Rational a, Rational b) {
        switch (op) {
            case ADD:
                return a.add(b);
            case SUBTRACT:
                return a.sub(b);
            case MULTIPLY:
                return a.mul(b);
            default:
                return a.div(b);
        }
    }

    // q +- irrational always is, q * / irrational only if q isnt 0
    private static boolean keepsIrrational(BinaryNode.Op op, Rational exact) {
        return op == BinaryNode.Op.ADD || op == BinaryNode.Op.SUBTRACT || !exact.isZero();
    }

    private static Rationality power(Rationality base, Rationality exponent) {
        if (!base.isExact() || !exponent.isExact()) {
            return Rationality.UNKNOWN;
        }
        Rational e = exponent.getValue();
        if (e.isInteger()) {
            return Rationality.exact(ExactPowers.power(base.getValue(), e.getNum()));
        }
        Rationality root = root(base.getValue(), e.getDen());
        if (!root.isExact()) {
            return root;
        }
        return Rationality.exact(ExactPowers.power(root.getValue(), e.getNum()));
    }

    // p/q has a rational root only if p and q both do
    private static Rationality root(Rational x, long degree) {
        if (x.isNegative() && degree % 2 == 0) {
            return Rationality.UNKNOWN;
        }
        Rational root = ExactPowers.root(x.isNegative() ? x.neg() : x, degree);
        if (root == null) {
            return Rationality.IRRATIONAL;
        }
        return Rationality.exact(x.isNegative() ? root.neg() : root);
    }

    @Override
    public Rationality visitFunction(FunctionNode node) {
        if (!node.getName().equals("\\sqrt")) {
            return Rationality.UNKNOWN;
        }
        Rationality argument = node.getArgument().accept(this);
        Rational degree = Rational.of(2);
        if (node.getExtra() != null) {
            Rationality extra = node.getExtra().accept(this);
            degree = extra.isExact() ? extra.getValue() : null;
        }
        if (!argument.isExact() || degree == null || !degree.isInteger() || degree.getNum() < 1) {
            return Rationality.UNKNOWN;
        }
        return root(argument.getValue(), degree.getNum());
    }

    @Override
    public Rationality visitCall(CallNode node) {
        return Rationality.UNKNOWN;
    }

    @Override
    public Rationality visitDerivative(DerivativeNode node) {
        return Rationality.UNKNOWN;
    }

    @Override
    public Rationality visitLimit(LimitNode node) {
        return Rationality.UNKNOWN;
    }

    @Override
    public Rationality visitPlusMinus(PlusMinusNode node) {
        return Rationality.UNKNOWN;
    }

    @Override
    public Rationality visitQuantifier(QuantifierNode node) {
        return Rationality.UNKNOWN;
    }

    @Override
    public Rationality visitRelation(RelationNode node) {
        return Rationality.UNKNOWN;
    }

    @Override
    public Rationality visitSeries(SeriesNode node) {
        return Rationality.UNKNOWN;
    }

    @Override
    public Rationality visitSetLiteral(SetLiteralNode node) {
        return Rationality.UNKNOWN;
    }

    @Override
    public Rationality visitVariable(VariableNode node) {
        return Rationality.UNKNOWN;
    }

    @Override
    public Rationality visitVector(VectorNode node) {
        return Rationality.UNKNOWN;
    }
}
