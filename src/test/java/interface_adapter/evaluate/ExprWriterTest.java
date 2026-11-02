package interface_adapter.evaluate;

import entity.symbolic.Expr;
import entity.symbolic.Rational;
import interface_adapter.TextRun;
import org.junit.jupiter.api.Test;
import use_case.evaluate.OutputDataMapper;

import java.util.List;

import static entity.symbolic.Expr.add;
import static entity.symbolic.Expr.mul;
import static entity.symbolic.Expr.num;
import static entity.symbolic.Expr.pow;
import static entity.symbolic.Expr.var;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExprWriterTest {
    private static final Expr X = var("x");

    private static Expr n(long value) {
        return num(Rational.of(value), false);
    }

    private static Expr n(long top, long bottom) {
        return num(Rational.of(top, bottom), false);
    }

    private static String latex(Expr e) {
        return ExprWriter.latex(OutputDataMapper.of(e));
    }

    private static List<TextRun> runs(Expr e) {
        return ExprWriter.runs(OutputDataMapper.of(e));
    }

    private static String plain(Expr e) {
        return TextRun.plain(runs(e));
    }

    @Test
    void powerIsRaised() {
        Expr e = mul(n(3), pow(X, n(2)));
        assertEquals("3x^{2}", latex(e));
        List<TextRun> runs = runs(e);
        assertEquals("3x2", TextRun.plain(runs));
        assertEquals(new TextRun("2", true), runs.get(runs.size() - 1));
        assertFalse(runs.get(0).isRaised());
    }

    @Test
    void negativePowerGoesUnderAFraction() {
        Expr e = pow(add(X, n(1)), n(-2));
        assertEquals("\\frac{1}{(x+1)^{2}}", latex(e));
        List<TextRun> runs = runs(e);
        assertEquals("1/(x+1)2", TextRun.plain(runs));
        assertEquals(new TextRun("2", true), runs.get(runs.size() - 1));
    }

    @Test
    void halfPowerIsARoot() {
        Expr e = mul(n(1, 2), pow(X, n(-1, 2)));
        assertEquals("\\frac{1}{2\\sqrt{x}}", latex(e));
        assertEquals("1/(2√x)", plain(e));
    }

    @Test
    void sumBaseGetsParens() {
        Expr e = mul(n(6), pow(add(mul(n(2), X), n(1)), n(2)));
        assertEquals("6(2x+1)^{2}", latex(e));
        assertEquals("6(2x+1)2", plain(e));
    }

    @Test
    void functionsOfOneSymbolHaveNoParens() {
        Expr e = add(Expr.fn("sin", X), mul(X, Expr.fn("cos", X)));
        assertEquals("\\sin x+x\\cos x", latex(e));
        assertEquals("sin x+x cos x", plain(e));
    }

    @Test
    void functionOfASumHasParens() {
        Expr e = Expr.fn("ln", add(X, n(1)));
        assertEquals("\\ln(x+1)", latex(e));
        assertEquals("ln(x+1)", plain(e));
    }

    @Test
    void implicitDerivative() {
        Expr e = add(mul(n(2), X), mul(n(2), var("y"), Expr.dvar("y", "x")));
        assertEquals("2x+2y\\frac{dy}{dx}", latex(e));
        assertEquals("2x+2y dy/dx", plain(e));
    }

    @Test
    void minusSign() {
        Expr e = add(X, n(-1));
        assertEquals("x-1", latex(e));
        assertEquals("x−1", plain(e));
        assertEquals("-x", latex(mul(n(-1), X)));
    }

    @Test
    void eToTheX() {
        assertEquals("e^{x}", latex(pow(Expr.constE(), X)));
    }

    @Test
    void decimalsPrintTwoPlaces() {
        assertEquals("2.5x", latex(mul(num(Rational.of(5, 2), true), X)));
        assertEquals("0.33", latex(num(Rational.of(1, 3), true)));
    }

    @Test
    void numberTimesNumberGetsADot() {
        Expr e = mul(n(2), pow(n(3), X));
        assertEquals("2\\cdot 3^{x}", latex(e));
        assertEquals("2·3x", plain(e));
    }

    @Test
    void sumUnderAFraction() {
        Expr e = mul(add(X, n(1)), pow(X, n(-1)));
        assertEquals("\\frac{x+1}{x}", latex(e));
        assertEquals("(x+1)/x", plain(e));
    }

    @Test
    void rationalArithmetic() {
        assertEquals(Rational.of(1, 2), Rational.of(1, 3).add(Rational.of(1, 6)));
        assertEquals(Rational.of(-3, 4), Rational.of(3, -4));
        assertTrue(Rational.of(4, 2).isInteger());
        assertTrue(Rational.of(-1, 2).isNegative());
    }
}
