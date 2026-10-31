package entity.symbolic;

import org.junit.jupiter.api.Test;

import static entity.symbolic.Expr.add;
import static entity.symbolic.Expr.mul;
import static entity.symbolic.Expr.num;
import static entity.symbolic.Expr.pow;
import static entity.symbolic.Expr.var;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SimplifierTest {
    private static final Expr X = var("x");

    private static Expr n(long value) {
        return num(value);
    }

    // simplify, check it's stable and has no junk, give latex
    private static String s(Expr e) {
        Expr once = Simplifier.simplify(e);
        assertEquals(once, Simplifier.simplify(once), "not idempotent");
        String latex = ExprWriter.latex(once);
        clean(latex);
        return latex;
    }

    static void clean(String latex) {
        for (String junk : new String[] {"0x", "1x", "^{1}", "+-", "(x)", "--"}) {
            assertFalse(latex.contains(junk), latex + " has " + junk);
        }
    }

    @Test
    void likeTermsCollect() {
        assertEquals("2x", s(add(X, X)));
    }

    @Test
    void sameBasesMultiply() {
        assertEquals("x^{3}", s(mul(X, pow(X, n(2)))));
    }

    @Test
    void quotientRuleShape() {
        Expr v = add(X, n(1));
        Expr top = add(mul(n(1), v), mul(n(-1), X, n(1)));
        assertEquals("\\frac{1}{(x+1)^{2}}", s(mul(top, pow(v, n(-2)))));
    }

    @Test
    void chainStaysFactored() {
        Expr inner = add(mul(n(2), X), n(1));
        assertEquals("6(2x+1)^{2}", s(mul(n(3), pow(inner, n(2)), n(2))));
    }

    @Test
    void logDiffStaysFactored() {
        Expr e = mul(pow(X, X), add(mul(n(1), Expr.fn("ln", X)), mul(X, pow(X, n(-1)))));
        assertEquals("x^{x}(\\ln x+1)", s(e));
    }

    @Test
    void zerosAndOnesGo() {
        assertEquals("x", s(add(mul(n(0), X), mul(n(1), pow(X, n(1))))));
        assertEquals("1", s(pow(X, n(0))));
        assertEquals("0", s(mul(X, n(0))));
    }

    @Test
    void productInASumIsDistributed() {
        assertEquals("1", s(add(add(X, n(1)), mul(n(-1), X))));
        assertEquals("x^{2}+2x", s(add(mul(n(2), X, add(X, n(1))), mul(n(-1), pow(X, n(2))))));
    }

    @Test
    void rootsMultiplyBack() {
        Expr root = pow(X, num(Rational.of(1, 2), false));
        assertEquals("x", s(mul(root, root)));
        assertEquals("\\frac{1}{2\\sqrt{x}}", s(mul(num(Rational.of(1, 2), false), pow(X, num(Rational.of(-1, 2), false)))));
    }

    @Test
    void squareRootOfASquareIsNotX() {
        Expr e = pow(pow(X, n(2)), num(Rational.of(1, 2), false));
        assertEquals("\\sqrt{x^{2}}", s(e));
    }

    @Test
    void logOfE() {
        assertEquals("1", s(Expr.fn("ln", Expr.constE())));
        assertEquals("x", s(pow(Expr.constE(), Expr.fn("ln", X))));
    }

    @Test
    void decimalsStick() {
        assertEquals("3x", s(mul(num(Rational.of(3, 2), true), n(2), X)));
        assertEquals("0.75x", s(mul(num(Rational.of(3, 2), true), num(Rational.of(1, 2), false), X)));
    }

    @Test
    void numbersFold() {
        assertEquals("8", s(pow(n(2), n(3))));
        assertEquals("\\frac{1}{4}", s(pow(n(2), n(-2))));
        assertEquals("5", s(add(n(2), n(3))));
    }

    @Test
    void sumsOrderByDegree() {
        assertEquals("x^{2}+x+1", s(add(n(1), X, pow(X, n(2)))));
        assertEquals(s(add(var("y"), X)), s(add(X, var("y"))));
    }
}
