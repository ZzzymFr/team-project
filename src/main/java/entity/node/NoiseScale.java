package entity.node;

import entity.Context;
import entity.value.NumberValue;
import entity.value.Value;

// how big float noise in a plain formula can get, so 10^{-10}>0 stays true
public final class NoiseScale {
    // a few dozen roundings worth of eps
    static final double TOLERANCE = 64 * Math.ulp(1.0);
    private static final double EXACT_INTEGERS = 0x1p53;
    private static final NodeVisitor<Boolean> SUPPORT = new Support();

    private NoiseScale() {
    }

    // only literals, vars, + - * / ^ and named functions
    static boolean isSupported(Node node) {
        return node.accept(SUPPORT);
    }

    // rough abs float noise in a value, NaN if unknown
    public static double noiseOf(Node node, Context context) {
        if (!isSupported(node)) {
            return Double.NaN;
        }
        try {
            return Math.ulp(1.0) * of(node, context);
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    // NaN if it cant tell
    static double of(Node node, Context context) {
        return node.accept(new Scale(number(node, context), context));
    }

    private static double ofBinary(BinaryNode node, double value, Context context) {
        double a = number(node.getLeft(), context);
        double b = number(node.getRight(), context);
        double sa = of(node.getLeft(), context);
        double sb = of(node.getRight(), context);
        double own = Math.abs(value);
        switch (node.getOp()) {
            case ADD:
            case SUBTRACT:
                return sa + sb + own;
            case MULTIPLY:
                return sa * Math.abs(b) + Math.abs(a) * sb + own;
            case DIVIDE:
                return b == 0 ? Double.NaN : sa / Math.abs(b) + Math.abs(a) * sb / (b * b) + own;
            default:
                if (a == 0) {
                    return Double.NaN;
                }
                return Math.abs(b * value / a) * sa + Math.abs(value * Math.log(Math.abs(a))) * sb + own;
        }
    }

    private static double ofFunction(FunctionNode node, double value, Context context) {
        double x = number(node.getArgument(), context);
        double sx = of(node.getArgument(), context);
        double own = Math.abs(value);
        NamedFunction function = NamedFunction.of(node.getName());
        if (node.getExtra() == null) {
            return function.noise(x, value, sx) + own;
        }
        double extra = number(node.getExtra(), context);
        return function.noise(x, extra, value, sx, of(node.getExtra(), context)) + own;
    }

    private static double number(Node node, Context context) {
        Value value = node.evaluate(context);
        return value instanceof NumberValue ? ((NumberValue) value).getValue() : Double.NaN;
    }

    private static final class Support implements NodeVisitor<Boolean> {
        @Override
        public Boolean visitNumber(NumberNode node) {
            return true;
        }

        @Override
        public Boolean visitVariable(VariableNode node) {
            return true;
        }

        @Override
        public Boolean visitLiteral(LiteralNode node) {
            Value value = node.getValue();
            return value instanceof NumberValue && Double.isFinite(((NumberValue) value).getValue());
        }

        @Override
        public Boolean visitUnary(UnaryNode node) {
            UnaryNode.Op op = node.getOp();
            return (op == UnaryNode.Op.NEGATE || op == UnaryNode.Op.ABSOLUTE || op == UnaryNode.Op.PERCENT
                    || op == UnaryNode.Op.FACTORIAL) && isSupported(node.getOperand());
        }

        @Override
        public Boolean visitBinary(BinaryNode node) {
            switch (node.getOp()) {
                case ADD:
                case SUBTRACT:
                case MULTIPLY:
                case DIVIDE:
                case POWER:
                    return isSupported(node.getLeft()) && isSupported(node.getRight());
                default:
                    return false;
            }
        }

        @Override
        public Boolean visitFunction(FunctionNode node) {
            NamedFunction function = NamedFunction.of(node.getName());
            if (function == null || !function.hasNoise(node.getExtra() != null)) {
                return false;
            }
            for (Node child : node.children()) {
                if (!isSupported(child)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public Boolean visitCall(CallNode node) {
            return false;
        }

        @Override
        public Boolean visitDerivative(DerivativeNode node) {
            return false;
        }

        @Override
        public Boolean visitLimit(LimitNode node) {
            return false;
        }

        @Override
        public Boolean visitPlusMinus(PlusMinusNode node) {
            return false;
        }

        @Override
        public Boolean visitQuantifier(QuantifierNode node) {
            return false;
        }

        @Override
        public Boolean visitRelation(RelationNode node) {
            return false;
        }

        @Override
        public Boolean visitSeries(SeriesNode node) {
            return false;
        }

        @Override
        public Boolean visitSetLiteral(SetLiteralNode node) {
            return false;
        }

        @Override
        public Boolean visitVector(VectorNode node) {
            return false;
        }
    }

    // only gets nodes isSupported said yes to
    private static final class Scale implements NodeVisitor<Double> {
        private final double value;
        private final Context context;

        private Scale(double value, Context context) {
            this.value = value;
            this.context = context;
        }

        @Override
        public Double visitNumber(NumberNode node) {
            if (value == Math.rint(value) && Math.abs(value) < EXACT_INTEGERS) {
                // typed whole numbers are exact, 0.1 isnt
                return 0.0;
            }
            return Math.abs(value);
        }

        @Override
        public Double visitLiteral(LiteralNode node) {
            return Math.abs(value);
        }

        // a var stands in for a root that doubles cant hit exactly, so ~1 ulp off
        @Override
        public Double visitVariable(VariableNode node) {
            return Math.abs(value);
        }

        @Override
        public Double visitUnary(UnaryNode node) {
            double inner = of(node.getOperand(), context);
            switch (node.getOp()) {
                case PERCENT:
                    return inner / 100 + Math.abs(value);
                case FACTORIAL:
                    return Math.abs(value) * Math.max(1, number(node.getOperand(), context));
                default:
                    return inner;
            }
        }

        @Override
        public Double visitBinary(BinaryNode node) {
            return ofBinary(node, value, context);
        }

        @Override
        public Double visitFunction(FunctionNode node) {
            return ofFunction(node, value, context);
        }

        @Override
        public Double visitCall(CallNode node) {
            throw unsupported(node);
        }

        @Override
        public Double visitDerivative(DerivativeNode node) {
            throw unsupported(node);
        }

        @Override
        public Double visitLimit(LimitNode node) {
            throw unsupported(node);
        }

        @Override
        public Double visitPlusMinus(PlusMinusNode node) {
            throw unsupported(node);
        }

        @Override
        public Double visitQuantifier(QuantifierNode node) {
            throw unsupported(node);
        }

        @Override
        public Double visitRelation(RelationNode node) {
            throw unsupported(node);
        }

        @Override
        public Double visitSeries(SeriesNode node) {
            throw unsupported(node);
        }

        @Override
        public Double visitSetLiteral(SetLiteralNode node) {
            throw unsupported(node);
        }

        @Override
        public Double visitVector(VectorNode node) {
            throw unsupported(node);
        }

        private static IllegalStateException unsupported(Node node) {
            return new IllegalStateException("no noise scale for " + node.getClass().getSimpleName());
        }
    }
}
