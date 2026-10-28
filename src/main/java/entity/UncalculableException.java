package entity;

public class UncalculableException extends MathSyntaxException {
    // short reasons for the popup, plain words no latex
    public static final String TEXT = "Contains text";
    public static final String ELLIPSIS = "Has \"…\"";
    public static final String NOT_CALCULATION = "Not a calculation";
    public static final String INTEGRAL = "Integrals not supported";
    public static final String PARTIAL = "∂ needs a function";
    public static final String NOT_DEFINED = "Function not defined";
    public static final String NOT_AN_INPUT = "Not a function input";
    public static final String MAX_DERIVATIVE = "Max 4th derivative";
    public static final String SUM_RANGE = "Sum needs a range";
    public static final String TOO_MANY_TERMS = "Over 1,000,000 terms";
    public static final String MAX_PLUS_MINUS = "Max 4 ± signs";
    public static final String PLUS_MINUS_HERE = "± not allowed here";
    public static final String TOO_BIG = "Number too big";
    public static final String WRONG_KIND = "Wrong kind of value";
    public static final String NEEDS_BOOLEAN = "Logic needs true/false";
    public static final String VECTOR_SIZES = "Vector sizes differ";
    public static final String CROSS_3D = "× needs 3D vectors";
    public static final String INFINITE_SET = "Infinite set";
    public static final String SET_ALONE = "A set has no value";
    public static final String UNKNOWN_SET = "Unknown set";
    public static final String RATIONAL = "Can't tell if rational";
    public static final String NOTHING_TO_CHECK = "Nothing to check";
    public static final String OVER_RN = "Can't check over ℝⁿ";
    public static final String TOO_MANY_SOLUTIONS = "Too many solutions";
    public static final String TOO_FAST = "Changes too fast";
    public static final String BEYOND_RANGE = "Answer beyond ±100";
    public static final String LIMIT = "Limit unclear";
    public static final String SERIES = "Series too slow to tell";
    public static final String DERIVATIVE = "Derivative unstable";
    public static final String TOO_MUCH_WORK = "Formula too complex";
    public static final String UNDECIDED = "Can't decide";
    public static final String SETS_TOO_LARGE = "Sets too large";
    public static final String UNDEFINED_PART = "Has an undefined part";
    public static final String SUM_BOUNDS = "Bounds must be integers";
    public static final String ZERO_VECTOR = "Zero vector has no direction";

    public UncalculableException() {
        super("Uncalculable");
    }

    public UncalculableException(String reason) {
        super("Uncalculable — " + reason);
    }

    // e.g. sin⁻¹ is ambiguous
    public static String inverseOf(String function) {
        return function.substring(1) + "⁻¹ is ambiguous";
    }
}
