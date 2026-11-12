package entity.symbolic;

import entity.Context;
import entity.node.NamedFunction;

// an Expr as a number, letters from the context, same rules the nodes use
public final class ExprValue {
    private static final NamedFunction LOG = NamedFunction.of("\\log");
    // same 64 ulps NoiseScale allows
    private static final double NOISE = 64 * Math.ulp(1.0);
    private static final double SLOPE_STEP = 1e-7;

    private final Context context;
    // float noise in the last value, in ulps of 1 like NoiseScale
    private double scale;

    private ExprValue(Context context) {
        this.context = context;
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
        return new ExprValue(context).value(e);
    }

    // {value, how much float noise could be in it}, NaN noise = cant tell
    public static double[] withNoise(Expr e, Context context) {
        ExprValue v = new ExprValue(context);
        double value = v.value(e);
        return new double[] {value, NOISE * v.scale};
    }

    private double value(Expr e) {
        switch (e.kind()) {
            case NUM:
                double n = e.value().toDouble();
                scale = e.value().isInteger() ? 0 : Math.abs(n);
                return n;
            case VAR:
                return rounded(context.getVariable(e.name()));
            case E:
                return rounded(Math.E);
            case PI:
                return rounded(Math.PI);
            case ADD:
                return sum(e);
            case MUL:
                return product(e);
            case POW:
                return power(e);
            case FN:
                return function(e);
            case LOG:
                return log(e);
            default:
                throw new IllegalStateException("no value for " + e.kind());
        }
    }

    private double rounded(double v) {
        scale = Math.abs(v);
        return v;
    }

    private double sum(Expr e) {
        double total = 0;
        double noise = 0;
        for (Expr t : e.terms()) {
            total += value(t);
            noise += scale;
        }
        scale = noise + Math.abs(total);
        return total;
    }

    private double product(Expr e) {
        double total = 1;
        double noise = 0;
        for (Expr f : e.terms()) {
            double v = value(f);
            noise = noise * Math.abs(v) + Math.abs(total) * scale;
            total *= v;
        }
        scale = noise + Math.abs(total);
        return total;
    }

    private double power(Expr e) {
        double a = value(e.base());
        double sa = scale;
        double b = value(e.exponent());
        double sb = scale;
        double result = power(a, b, !hasLetter(e.exponent()));
        if (a == 0) {
            scale = sa == 0 && sb == 0 ? 0 : Double.NaN;
            return result;
        }
        scale = Math.abs(b * result / a) * sa + Math.abs(result * Math.log(Math.abs(a))) * sb + Math.abs(result);
        return result;
    }

    private double function(Expr e) {
        NamedFunction f = NamedFunction.of("\\" + e.name());
        double x = value(e.arg());
        double sx = scale;
        double result = f.apply(x);
        // small step, but a few ulps so x+h isnt just x (sin at 10^12)
        double h = Math.max(4 * Math.ulp(x), SLOPE_STEP * Math.min(1, Math.abs(x)));
        double slope = Math.abs(f.apply(x + h) - f.apply(x - h)) / (2 * h);
        scale = slope * sx + Math.abs(result);
        return result;
    }

    private double log(Expr e) {
        double x = value(e.arg());
        double sx = scale;
        if (e.logBase() == null) {
            double result = LOG.apply(x);
            scale = sx / Math.abs(x * Math.log(10)) + Math.abs(result);
            return result;
        }
        double base = value(e.logBase());
        double sb = scale;
        double result = LOG.apply(x, base);
        double lnBase = Math.abs(Math.log(base));
        scale = sx / Math.abs(x * lnBase) + Math.abs(result) * sb / Math.abs(base * lnBase) + Math.abs(result);
        return result;
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
