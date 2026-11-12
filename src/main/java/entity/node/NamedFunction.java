package entity.node;

import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

// every \name function: how its written, its value, how much float noise it passes on
public enum NamedFunction {
    SIN("\\sin", Math::sin, (x, value, noise) -> Math.abs(Math.cos(x)) * noise),
    COS("\\cos", Math::cos, (x, value, noise) -> Math.abs(Math.sin(x)) * noise),
    TAN("\\tan", x -> Math.abs(Math.cos(x)) < 1e-12 ? Double.NaN : Math.tan(x),
            (x, value, noise) -> (1 + value * value) * noise),
    LN("\\ln", NamedFunction::logarithm, (x, value, noise) -> noise / Math.abs(x)),
    LOG("\\log", Syntax.LOG, x -> x <= 0 ? Double.NaN : Math.log10(x), NamedFunction::log,
            (x, value, noise) -> noise / (Math.abs(x) * Math.log(10)), NamedFunction::logNoise),
    SQRT("\\sqrt", Syntax.ROOT, Math::sqrt, NamedFunction::root,
            (x, value, noise) -> rootNoise(x, 2, value, noise, 0), NamedFunction::rootNoise),
    ARCSIN("\\arcsin", Math::asin, (x, value, noise) -> noise / Math.sqrt(1 - x * x)),
    ARCCOS("\\arccos", Math::acos, (x, value, noise) -> noise / Math.sqrt(1 - x * x)),
    ARCTAN("\\arctan", Math::atan, (x, value, noise) -> noise / (1 + x * x)),
    SINH("\\sinh", Math::sinh, (x, value, noise) -> Math.cosh(x) * noise),
    COSH("\\cosh", Math::cosh, (x, value, noise) -> Math.abs(Math.sinh(x)) * noise),
    TANH("\\tanh", Math::tanh, (x, value, noise) -> (1 - value * value) * noise),
    EXP("\\exp", Math::exp, (x, value, noise) -> Math.abs(value) * noise),
    COT("\\cot", x -> Math.abs(Math.sin(x)) < 1e-12 ? Double.NaN : Math.cos(x) / Math.sin(x),
            (x, value, noise) -> (1 + value * value) * noise),
    SEC("\\sec", x -> Math.abs(Math.cos(x)) < 1e-12 ? Double.NaN : 1 / Math.cos(x),
            (x, value, noise) -> Math.abs(value * Math.tan(x)) * noise),
    CSC("\\csc", x -> Math.abs(Math.sin(x)) < 1e-12 ? Double.NaN : 1 / Math.sin(x),
            (x, value, noise) -> Math.abs(value / Math.tan(x)) * noise),
    // jumps, so no noise formula
    FLOOR("\\lfloor", Syntax.BRACKETS, Math::floor, null, null, null),
    CEIL("\\lceil", Syntax.BRACKETS, Math::ceil, null, null, null),
    // lists fold left, \max(a,b,c) = max(max(a,b),c)
    MAX("\\max", Syntax.LIST, x -> x, Math::max, (x, value, noise) -> noise,
            (x, other, value, noise, otherNoise) -> Math.max(noise, otherNoise)),
    MIN("\\min", Syntax.LIST, x -> x, Math::min, (x, value, noise) -> noise,
            (x, other, value, noise, otherNoise) -> Math.max(noise, otherNoise)),
    GCD("\\gcd", Syntax.LIST, x -> gcd(x, 0), NamedFunction::gcd, null, null);

    // how the argument is written
    public enum Syntax { NAMED, LOG, ROOT, BRACKETS, LIST }

    interface Noise {
        double of(double x, double value, double noise);
    }

    interface ExtraNoise {
        double of(double x, double extra, double value, double noise, double extraNoise);
    }

    private static final Map<String, NamedFunction> BY_LATEX = new HashMap<>();

    static {
        for (NamedFunction function : values()) {
            BY_LATEX.put(function.latex, function);
        }
    }

    private final String latex;
    private final Syntax syntax;
    private final DoubleUnaryOperator value;
    private final DoubleBinaryOperator withExtra;
    private final Noise noise;
    private final ExtraNoise extraNoise;

    NamedFunction(String latex, DoubleUnaryOperator value, Noise noise) {
        this(latex, Syntax.NAMED, value, null, noise, null);
    }

    NamedFunction(String latex, Syntax syntax, DoubleUnaryOperator value, DoubleBinaryOperator withExtra,
                  Noise noise, ExtraNoise extraNoise) {
        this.latex = latex;
        this.syntax = syntax;
        this.value = value;
        this.withExtra = withExtra;
        this.noise = noise;
        this.extraNoise = extraNoise;
    }

    // null if its not one
    public static NamedFunction of(String latex) {
        return BY_LATEX.get(latex);
    }

    public String getLatex() {
        return latex;
    }

    public Syntax getSyntax() {
        return syntax;
    }

    // \lfloor .. \rfloor
    public String getClosing() {
        return syntax == Syntax.BRACKETS ? "\\r" + latex.substring(2) : null;
    }

    public static boolean isClosing(String token) {
        for (NamedFunction function : values()) {
            if (token.equals(function.getClosing())) {
                return true;
            }
        }
        return false;
    }

    public double apply(double x) {
        return value.applyAsDouble(x);
    }

    // log base, root degree, next list item
    public double apply(double x, double extra) {
        if (withExtra == null) {
            throw new IllegalStateException(latex + " takes one input");
        }
        return withExtra.applyAsDouble(x, extra);
    }

    boolean hasNoise(boolean withExtra) {
        return withExtra ? extraNoise != null : noise != null;
    }

    // noise in the answer from noise in x, w/o its own rounding
    double noise(double x, double result, double inputNoise) {
        return noise.of(x, result, inputNoise);
    }

    double noise(double x, double extra, double result, double inputNoise, double extraNoise) {
        return this.extraNoise.of(x, extra, result, inputNoise, extraNoise);
    }

    private static double logNoise(double x, double base, double value, double noise, double baseNoise) {
        double logBase = Math.abs(Math.log(base));
        return noise / (Math.abs(x) * logBase) + Math.abs(value) * baseNoise / (Math.abs(base) * logBase);
    }

    // degree noise left out, its almost always a typed whole number
    private static double rootNoise(double x, double degree, double value, double noise, double degreeNoise) {
        if (x == 0) {
            return Double.NaN;
        }
        return Math.abs(value / (degree * x)) * noise;
    }

    private static double log(double x, double base) {
        if (base <= 0 || base == 1) {
            return Double.NaN;
        }
        return logarithm(x) / Math.log(base);
    }

    private static double logarithm(double x) {
        if (x <= 0) {
            return Double.NaN;
        }
        return Math.log(x);
    }

    private static double root(double x, double degree) {
        if (degree == 2) {
            return Math.sqrt(x);
        }
        boolean isOddWholeNumber = degree == Math.floor(degree) && Math.abs(degree % 2) == 1;
        if (x < 0 && isOddWholeNumber) {
            return -Math.pow(-x, 1 / degree);
        }
        return Math.pow(x, 1 / degree);
    }

    // whole numbers only
    private static double gcd(double a, double b) {
        if (a != Math.rint(a) || b != Math.rint(b) || Double.isInfinite(a) || Double.isInfinite(b)) {
            return Double.NaN;
        }
        double big = Math.abs(a);
        double small = Math.abs(b);
        while (small != 0) {
            double rest = big % small;
            big = small;
            small = rest;
        }
        return big;
    }
}
