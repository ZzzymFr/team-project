package entity.symbolic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

// immutable math expr for symbolic d/dx; a/b = a*b^-1, -a = -1*a, sqrt = ^(1/2)
public final class Expr {
    public enum Kind { NUM, VAR, E, PI, ADD, MUL, POW, FN, LOG, DVAR }

    private static final Expr E_CONST = new Expr(Kind.E, null, false, null, null, null, null);
    private static final Expr PI_CONST = new Expr(Kind.PI, null, false, null, null, null, null);

    private final Kind kind;
    private final Rational value;
    private final boolean decimal;
    private final String name;      // var name, fn name (sin cos tan ln), dvar y
    private final List<Expr> terms;
    private final Expr first;       // pow base, fn/log arg
    private final Expr second;      // pow exponent, log base (null = 10)

    private Expr(Kind kind, Rational value, boolean decimal, String name, List<Expr> terms, Expr first,
                 Expr second) {
        this.kind = kind;
        this.value = value;
        this.decimal = decimal;
        this.name = name;
        this.terms = terms;
        this.first = first;
        this.second = second;
    }

    public static Expr num(Rational value, boolean decimal) {
        return new Expr(Kind.NUM, value, decimal, null, null, null, null);
    }

    public static Expr num(long value) {
        return num(Rational.of(value), false);
    }

    public static Expr var(String name) {
        return new Expr(Kind.VAR, null, false, name, null, null, null);
    }

    public static Expr constE() {
        return E_CONST;
    }

    public static Expr constPi() {
        return PI_CONST;
    }

    public static Expr add(List<Expr> terms) {
        return new Expr(Kind.ADD, null, false, null, Collections.unmodifiableList(new ArrayList<>(terms)), null, null);
    }

    public static Expr add(Expr... terms) {
        return add(Arrays.asList(terms));
    }

    public static Expr mul(List<Expr> factors) {
        return new Expr(Kind.MUL, null, false, null, Collections.unmodifiableList(new ArrayList<>(factors)), null,
                null);
    }

    public static Expr mul(Expr... factors) {
        return mul(Arrays.asList(factors));
    }

    public static Expr pow(Expr base, Expr exponent) {
        return new Expr(Kind.POW, null, false, null, null, base, exponent);
    }

    // name is sin cos tan or ln
    public static Expr fn(String name, Expr arg) {
        return new Expr(Kind.FN, null, false, name, null, arg, null);
    }

    public static Expr log(Expr arg, Expr base) {
        return new Expr(Kind.LOG, null, false, null, null, arg, base);
    }

    // dy/dx
    public static Expr dvar(String y, String x) {
        return new Expr(Kind.DVAR, null, false, y, Collections.singletonList(var(x)), null, null);
    }

    public Kind kind() {
        return kind;
    }

    public Rational value() {
        return value;
    }

    public boolean isDecimal() {
        return decimal;
    }

    public String name() {
        return name;
    }

    public List<Expr> terms() {
        return terms;
    }

    public Expr base() {
        return first;
    }

    public Expr exponent() {
        return second;
    }

    public Expr arg() {
        return first;
    }

    public Expr logBase() {
        return second;
    }

    // the x in dy/dx
    public String withRespectTo() {
        return terms.get(0).name;
    }

    public boolean isNum() {
        return kind == Kind.NUM;
    }

    public boolean isNum(long n) {
        return kind == Kind.NUM && value.equals(Rational.of(n));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Expr)) {
            return false;
        }
        Expr e = (Expr) o;
        return kind == e.kind && decimal == e.decimal && Objects.equals(value, e.value)
                && Objects.equals(name, e.name) && Objects.equals(terms, e.terms)
                && Objects.equals(first, e.first) && Objects.equals(second, e.second);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, value, decimal, name, terms, first, second);
    }

    @Override
    public String toString() {
        return ExprWriter.latex(this);
    }
}
