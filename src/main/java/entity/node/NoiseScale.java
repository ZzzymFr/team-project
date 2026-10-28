package entity.node;

import entity.Context;
import entity.value.NumberValue;
import entity.value.Value;

// how big float noise in a plain formula can get, so 10^{-10}>0 stays true
final class NoiseScale {
    // a few dozen roundings worth of eps
    static final double TOLERANCE = 64 * Math.ulp(1.0);
    private static final double EXACT_INTEGERS = 0x1p53;

    private NoiseScale() {
    }

    // only literals, vars, + - * / ^ and named functions
    static boolean isSupported(Node node) {
        if (node instanceof NumberNode || node instanceof VariableNode) {
            return true;
        }
        if (node instanceof LiteralNode) {
            Value value = ((LiteralNode) node).getValue();
            return value instanceof NumberValue && isFinite(((NumberValue) value).getValue());
        }
        if (node instanceof UnaryNode) {
            UnaryNode.Op op = ((UnaryNode) node).getOp();
            return (op == UnaryNode.Op.NEGATE || op == UnaryNode.Op.ABSOLUTE || op == UnaryNode.Op.PERCENT
                    || op == UnaryNode.Op.FACTORIAL) && isSupported(((UnaryNode) node).getOperand());
        }
        if (node instanceof BinaryNode) {
            BinaryNode binary = (BinaryNode) node;
            switch (binary.getOp()) {
                case ADD:
                case SUBTRACT:
                case MULTIPLY:
                case DIVIDE:
                case POWER:
                    return isSupported(binary.getLeft()) && isSupported(binary.getRight());
                default:
                    return false;
            }
        }
        if (node instanceof FunctionNode) {
            for (Node child : node.children()) {
                if (!isSupported(child)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    // rough abs float noise in a value, NaN if unknown
    static double noiseOf(Node node, Context context) {
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
        double value = number(node, context);
        if (node instanceof NumberNode && value == Math.rint(value) && Math.abs(value) < EXACT_INTEGERS) {
            // typed whole numbers are exact, 0.1 isnt
            return 0;
        }
        // a var stands in for a root that doubles cant hit exactly, so ~1 ulp off
        if (node instanceof NumberNode || node instanceof LiteralNode || node instanceof VariableNode) {
            return Math.abs(value);
        }
        if (node instanceof UnaryNode) {
            UnaryNode unary = (UnaryNode) node;
            double inner = of(unary.getOperand(), context);
            switch (unary.getOp()) {
                case PERCENT:
                    return inner / 100 + Math.abs(value);
                case FACTORIAL:
                    return Math.abs(value) * Math.max(1, number(unary.getOperand(), context));
                default:
                    return inner;
            }
        }
        if (node instanceof BinaryNode) {
            return ofBinary((BinaryNode) node, value, context);
        }
        return ofFunction((FunctionNode) node, value, context);
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
        switch (node.getName()) {
            case "\\sin":
                return Math.abs(Math.cos(x)) * sx + own;
            case "\\cos":
                return Math.abs(Math.sin(x)) * sx + own;
            case "\\tan":
                return (1 + value * value) * sx + own;
            case "\\ln":
                return sx / Math.abs(x) + own;
            case "\\log":
                if (node.getExtra() == null) {
                    return sx / (Math.abs(x) * Math.log(10)) + own;
                }
                double base = number(node.getExtra(), context);
                double logBase = Math.abs(Math.log(base));
                return sx / (Math.abs(x) * logBase)
                        + Math.abs(value) * of(node.getExtra(), context) / (Math.abs(base) * logBase) + own;
            default:
                if (x == 0) {
                    return Double.NaN;
                }
                double degree = node.getExtra() == null ? 2 : number(node.getExtra(), context);
                return Math.abs(value / (degree * x)) * sx + own;
        }
    }

    private static double number(Node node, Context context) {
        Value value = node.evaluate(context);
        return value instanceof NumberValue ? ((NumberValue) value).getValue() : Double.NaN;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
