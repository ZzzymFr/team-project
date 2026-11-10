package entity.symbolic;

import entity.Context;
import entity.node.NamedFunction;

// an Expr as a number, letters from the context, same rules the nodes use
public final class ExprValue {
    private static final NamedFunction LOG = NamedFunction.of("\\log");

    private ExprValue() {
    }

    // no rule for dy/dx or a name NamedFunction doesnt have
    public static boolean canEvaluate(Expr e) {
        switch (e.kind()) {
            case NUM:
            case VAR:
            case E:
            case PI:
                return true;
            case ADD:
            case MUL:
                for (Expr t : e.terms()) {
                    if (!canEvaluate(t)) {
                        return false;
                    }
                }
                return true;
            case POW:
                return canEvaluate(e.base()) && canEvaluate(e.exponent());
            case FN:
                return NamedFunction.of("\\" + e.name()) != null && canEvaluate(e.arg());
            case LOG:
                return canEvaluate(e.arg()) && (e.logBase() == null || canEvaluate(e.logBase()));
            default:
                return false;
        }
    }

    public static double of(Expr e, Context context) {
        switch (e.kind()) {
            case NUM:
                return e.value().toDouble();
            case VAR:
                return context.getVariable(e.name());
            case E:
                return Math.E;
            case PI:
                return Math.PI;
            case ADD:
                double sum = 0;
                for (Expr t : e.terms()) {
                    sum += of(t, context);
                }
                return sum;
            case MUL:
                double product = 1;
                for (Expr f : e.terms()) {
                    product *= of(f, context);
                }
                return product;
            case POW:
                return power(of(e.base(), context), of(e.exponent(), context), !hasLetter(e.exponent()));
            case FN:
                return NamedFunction.of("\\" + e.name()).apply(of(e.arg(), context));
            case LOG:
                double x = of(e.arg(), context);
                return e.logBase() == null ? LOG.apply(x) : LOG.apply(x, of(e.logBase(), context));
            default:
                throw new IllegalStateException("no value for " + e.kind());
        }
    }

    // copy of BinaryNode's ^, its private there
    private static double power(double a, double b, boolean constantExponent) {
        if (a == 0 && b < 0) {
            return Double.NaN;
        }
        if (a < 0 && b != Math.floor(b) && constantExponent) {
            return oddRootPower(a, b);
        }
        return Math.pow(a, b);
    }

    private static double oddRootPower(double a, double b) {
        for (int q = 3; q <= 99; q += 2) {
            double p = Math.rint(b * q);
            if (Math.abs(b * q - p) <= 1e-9 * q) {
                double magnitude = Math.pow(-a, b);
                return Math.abs(p % 2) == 1 ? -magnitude : magnitude;
            }
        }
        return Double.NaN;
    }

    private static boolean hasLetter(Expr e) {
        switch (e.kind()) {
            case NUM:
            case E:
            case PI:
                return false;
            case VAR:
            case DVAR:
                return true;
            case ADD:
            case MUL:
                for (Expr t : e.terms()) {
                    if (hasLetter(t)) {
                        return true;
                    }
                }
                return false;
            case POW:
                return hasLetter(e.base()) || hasLetter(e.exponent());
            default:
                return hasLetter(e.arg()) || (e.logBase() != null && hasLetter(e.logBase()));
        }
    }
}
