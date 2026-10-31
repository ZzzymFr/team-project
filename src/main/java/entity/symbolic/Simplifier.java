package entity.symbolic;

import entity.UncalculableException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// tidies d/dx output: fold numbers, collect terms, merge bases; not a full CAS
public final class Simplifier {
    private static final int MAX_PASSES = 8;
    private static final int MAX_STEPS = 200_000;
    private static final int MAX_POWER = 64;

    private int steps;

    private Simplifier() {
    }

    // same input -> same output, and simplify(simplify(e)) == simplify(e)
    public static Expr simplify(Expr e) {
        Simplifier s = new Simplifier();
        Expr current = e;
        for (int i = 0; i < MAX_PASSES; i++) {
            Expr next = s.run(current);
            if (next.equals(current)) {
                return next;
            }
            current = next;
        }
        return current;
    }

    private void spend() {
        if (++steps > MAX_STEPS) {
            throw new UncalculableException(UncalculableException.TOO_MUCH_WORK);
        }
    }

    private Expr run(Expr e) {
        spend();
        switch (e.kind()) {
            case ADD:
                List<Expr> terms = new ArrayList<>();
                for (Expr t : e.terms()) {
                    terms.add(run(t));
                }
                return sum(terms);
            case MUL:
                List<Expr> factors = new ArrayList<>();
                for (Expr f : e.terms()) {
                    factors.add(run(f));
                }
                return product(factors);
            case POW:
                return power(run(e.base()), run(e.exponent()));
            case FN:
                return function(e.name(), run(e.arg()));
            case LOG:
                Expr arg = run(e.arg());
                if (arg.isNum(1)) {
                    return Expr.num(0);
                }
                return Expr.log(arg, e.logBase() == null ? null : run(e.logBase()));
            default:
                return e;
        }
    }

    private static Expr function(String name, Expr arg) {
        if ("ln".equals(name)) {
            if (arg.kind() == Expr.Kind.E) {
                return Expr.num(1);
            }
            if (arg.isNum(1)) {
                return Expr.num(0);
            }
            if (arg.kind() == Expr.Kind.POW && arg.base().kind() == Expr.Kind.E) {
                return arg.exponent();
            }
        } else if (arg.isNum(0)) {
            return Expr.num("cos".equals(name) ? 1 : 0);
        }
        return Expr.fn(name, arg);
    }

    // ---- powers

    private Expr power(Expr base, Expr exp) {
        spend();
        if (exp.isNum() && exp.value().isZero()) {
            return Expr.num(1);
        }
        if (exp.isNum() && exp.value().isOne()) {
            return base;
        }
        if (base.isNum(1)) {
            return base;
        }
        if (base.isNum() && base.value().isZero() && exp.isNum() && !exp.value().isNegative()) {
            return base;
        }
        if (base.isNum() && exp.isNum() && exp.value().isInteger()) {
            Expr folded = numberPower(base, exp);
            if (folded != null) {
                return folded;
            }
        }
        if (base.kind() == Expr.Kind.E && exp.kind() == Expr.Kind.FN && "ln".equals(exp.name())) {
            return exp.arg();
        }
        boolean wholeExp = exp.isNum() && exp.value().isInteger();
        // (u^a)^n = u^(an) only for whole n, so sqrt(x^2) stays
        if (base.kind() == Expr.Kind.POW && wholeExp) {
            return power(base.base(), product(List.of(base.exponent(), exp)));
        }
        if (base.kind() == Expr.Kind.MUL && wholeExp) {
            List<Expr> factors = new ArrayList<>();
            for (Expr f : base.terms()) {
                factors.add(power(f, exp));
            }
            return product(factors);
        }
        return Expr.pow(base, exp);
    }

    private static Expr numberPower(Expr base, Expr exp) {
        long n = exp.value().getNum();
        if (Math.abs(n) > MAX_POWER || (base.value().isZero() && n < 0)) {
            return null;
        }
        Rational result = Rational.ONE;
        for (long i = 0; i < Math.abs(n); i++) {
            result = result.mul(base.value());
        }
        if (n < 0) {
            result = Rational.ONE.div(result);
        }
        return Expr.num(result, base.isDecimal() || exp.isDecimal());
    }

    // ---- products

    private Expr product(List<Expr> input) {
        spend();
        Rational coefficient = Rational.ONE;
        boolean decimal = false;
        Map<Expr, List<Expr>> exponents = new LinkedHashMap<>();
        List<Expr> pending = new ArrayList<>(input);
        while (!pending.isEmpty()) {
            Expr f = pending.remove(0);
            spend();
            if (f.kind() == Expr.Kind.MUL) {
                pending.addAll(0, f.terms());
            } else if (f.isNum()) {
                coefficient = coefficient.mul(f.value());
                decimal |= f.isDecimal();
            } else if (f.kind() == Expr.Kind.POW) {
                exponents.computeIfAbsent(f.base(), k -> new ArrayList<>()).add(f.exponent());
            } else {
                exponents.computeIfAbsent(f, k -> new ArrayList<>()).add(Expr.num(1));
            }
        }
        if (coefficient.isZero()) {
            return Expr.num(Rational.ZERO, decimal);
        }
        List<Expr> factors = new ArrayList<>();
        boolean again = false;
        for (Map.Entry<Expr, List<Expr>> entry : exponents.entrySet()) {
            List<Expr> exps = entry.getValue();
            Expr exp = exps.size() == 1 ? exps.get(0) : sum(exps);
            Expr merged = power(entry.getKey(), exp);
            if (merged.isNum() || merged.kind() == Expr.Kind.MUL) {
                again = true;  // folded to a number or split up, go round again
            }
            factors.add(merged);
        }
        if (again) {
            factors.add(Expr.num(coefficient, decimal));
            return product(factors);
        }
        factors.sort(FACTOR_ORDER);
        if (factors.isEmpty()) {
            return Expr.num(coefficient, decimal);
        }
        if (coefficient.isOne()) {
            return factors.size() == 1 ? factors.get(0) : Expr.mul(factors);
        }
        factors.add(0, Expr.num(coefficient, decimal));
        return Expr.mul(factors);
    }

    // ---- sums

    private Expr sum(List<Expr> input) {
        spend();
        Rational constant = Rational.ZERO;
        boolean decimal = false;
        Map<Expr, Rational> coefficients = new LinkedHashMap<>();
        Map<Expr, Boolean> decimals = new LinkedHashMap<>();
        List<Expr> pending = new ArrayList<>(input);
        while (!pending.isEmpty()) {
            Expr t = pending.remove(0);
            spend();
            if (t.kind() == Expr.Kind.ADD) {
                pending.addAll(0, t.terms());
                continue;
            }
            if (t.kind() == Expr.Kind.MUL && hasSumFactor(t)) {
                pending.addAll(0, distribute(t));
                continue;
            }
            if (t.isNum()) {
                constant = constant.add(t.value());
                decimal |= t.isDecimal();
                continue;
            }
            Rational c = Rational.ONE;
            boolean d = false;
            Expr rest = t;
            if (t.kind() == Expr.Kind.MUL && t.terms().get(0).isNum()) {
                c = t.terms().get(0).value();
                d = t.terms().get(0).isDecimal();
                List<Expr> others = t.terms().subList(1, t.terms().size());
                rest = others.size() == 1 ? others.get(0) : Expr.mul(others);
            }
            coefficients.merge(rest, c, Rational::add);
            decimals.merge(rest, d, Boolean::logicalOr);
        }
        List<Expr> terms = new ArrayList<>();
        for (Map.Entry<Expr, Rational> entry : coefficients.entrySet()) {
            Rational c = entry.getValue();
            if (!c.isZero()) {
                terms.add(c.isOne() ? entry.getKey()
                        : product(List.of(Expr.num(c, decimals.get(entry.getKey())), entry.getKey())));
            }
        }
        terms.sort(TERM_ORDER);
        if (!constant.isZero() || terms.isEmpty()) {
            terms.add(Expr.num(constant, decimal));
        }
        return terms.size() == 1 ? terms.get(0) : Expr.add(terms);
    }

    private static boolean hasSumFactor(Expr product) {
        for (Expr f : product.terms()) {
            if (f.kind() == Expr.Kind.ADD) {
                return true;
            }
        }
        return false;
    }

    // a(b+c) -> ab + ac, only called for products sitting in a sum
    private List<Expr> distribute(Expr product) {
        List<Expr> others = new ArrayList<>();
        Expr sumFactor = null;
        for (Expr f : product.terms()) {
            if (sumFactor == null && f.kind() == Expr.Kind.ADD) {
                sumFactor = f;
            } else {
                others.add(f);
            }
        }
        List<Expr> out = new ArrayList<>();
        for (Expr t : sumFactor.terms()) {
            List<Expr> factors = new ArrayList<>(others);
            factors.add(t);
            out.add(product(factors));
        }
        return out;
    }

    // ---- ordering

    // number, pi, letters, e^.., dy/dx, (sums), then functions so sin x can go bare
    private static int rank(Expr f) {
        Expr base = f.kind() == Expr.Kind.POW ? f.base() : f;
        switch (base.kind()) {
            case NUM:
                return 0;
            case PI:
                return 1;
            case VAR:
                return 2;
            case E:
                return 3;
            case DVAR:
                return 4;
            case FN:
            case LOG:
                return 6;
            default:
                return 5;
        }
    }

    private static final Comparator<Expr> FACTOR_ORDER =
            Comparator.comparingInt(Simplifier::rank).thenComparing(Simplifier::compare);

    // higher degree first, ties by structure
    private static final Comparator<Expr> TERM_ORDER =
            Comparator.comparing(Simplifier::degree).reversed().thenComparing(Simplifier::compare);

    private static Rational degree(Expr term) {
        Rational total = Rational.ZERO;
        for (Expr f : term.kind() == Expr.Kind.MUL ? term.terms() : List.of(term)) {
            if (f.kind() == Expr.Kind.VAR) {
                total = total.add(Rational.ONE);
            } else if (f.kind() == Expr.Kind.POW && f.base().kind() == Expr.Kind.VAR && f.exponent().isNum()) {
                total = total.add(f.exponent().value());
            }
        }
        return total;
    }

    static int compare(Expr a, Expr b) {
        if (a == b) {
            return 0;
        }
        int c = Integer.compare(a.kind().ordinal(), b.kind().ordinal());
        if (c != 0) {
            return c;
        }
        switch (a.kind()) {
            case NUM:
                return a.value().compareTo(b.value());
            case VAR:
                return a.name().compareTo(b.name());
            case DVAR:
            case FN:
                c = a.name().compareTo(b.name());
                break;
            default:
                c = 0;
        }
        if (c == 0 && a.terms() != null) {
            c = compareLists(a.terms(), b.terms());
        }
        if (c == 0 && a.base() != null) {
            c = compare(a.base(), b.base());
        }
        if (c == 0) {
            c = compareNullable(a.exponent(), b.exponent());
        }
        return c;
    }

    private static int compareLists(List<Expr> a, List<Expr> b) {
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            int c = compare(a.get(i), b.get(i));
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(a.size(), b.size());
    }

    private static int compareNullable(Expr a, Expr b) {
        if (a == null || b == null) {
            return a == null ? (b == null ? 0 : -1) : 1;
        }
        return compare(a, b);
    }
}
