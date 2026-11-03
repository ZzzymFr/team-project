package use_case.evaluate;

import java.util.List;

// written out answer (the derivative) as plain data so no Expr leaves the use case
public final class ExprOutputData {
    public enum Kind { NUM, VAR, E, PI, ADD, MUL, POW, FN, LOG, DVAR }

    private final Kind kind;
    private final long num;
    private final long den;
    private final boolean decimal;
    private final String name;
    private final List<ExprOutputData> terms;
    private final ExprOutputData first;   // pow base, fn/log arg
    private final ExprOutputData second;  // pow exponent, log base (null = 10)

    ExprOutputData(Kind kind, long num, long den, boolean decimal, String name, List<ExprOutputData> terms,
                   ExprOutputData first, ExprOutputData second) {
        this.kind = kind;
        this.num = num;
        this.den = den;
        this.decimal = decimal;
        this.name = name;
        this.terms = terms;
        this.first = first;
        this.second = second;
    }

    public static ExprOutputData num(long num, long den, boolean decimal) {
        return new ExprOutputData(Kind.NUM, num, den, decimal, null, null, null, null);
    }

    public static ExprOutputData num(long value) {
        return num(value, 1, false);
    }

    public static ExprOutputData mul(List<ExprOutputData> factors) {
        return new ExprOutputData(Kind.MUL, 0, 0, false, null, List.copyOf(factors), null, null);
    }

    public static ExprOutputData pow(ExprOutputData base, ExprOutputData exponent) {
        return new ExprOutputData(Kind.POW, 0, 0, false, null, null, base, exponent);
    }

    public Kind kind() {
        return kind;
    }

    public long num() {
        return num;
    }

    public long den() {
        return den;
    }

    public boolean isDecimal() {
        return decimal;
    }

    public String name() {
        return name;
    }

    public List<ExprOutputData> terms() {
        return terms;
    }

    public ExprOutputData base() {
        return first;
    }

    public ExprOutputData exponent() {
        return second;
    }

    public ExprOutputData arg() {
        return first;
    }

    public ExprOutputData logBase() {
        return second;
    }

    // the x in dy/dx
    public String withRespectTo() {
        return terms.get(0).name;
    }

    public boolean isNum() {
        return kind == Kind.NUM;
    }

    public boolean isNegative() {
        return num < 0;
    }

    public boolean isInteger() {
        return den == 1;
    }
}
