package entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LatexFunctionParserTest {
    private static final double EPS = 1e-9;
    private final LatexFunctionParser parser = new LatexFunctionParser();

    private double eval(String latex, double x) throws LatexParseException {
        return parser.parse(latex).evaluate(x);
    }

    @Test
    void polynomialWithImplicitMultiplication() throws LatexParseException {
        assertEquals(2 * 9 + 3 * 3 - 1, eval("y=2x^2+3x-1", 3), EPS);
        assertEquals(10, eval("x^{2}+1", 3), EPS);
    }

    @Test
    void functionNotationOnEitherSide() throws LatexParseException {
        assertEquals(17, eval("f(x)=3x+8", 3), EPS);
        assertEquals(4, eval("x^2=y", 2), EPS);
    }

    @Test
    void validationSampleFromModel() throws LatexParseException {
        double x = 1.3;
        assertEquals(Math.sin(x - Math.PI / 2) + 1, eval("y=\\sin(x-\\frac{\\pi}{2})+1", x), EPS);
    }

    @Test
    void fractionsRootsAndAbs() throws LatexParseException {
        assertEquals(0.5, eval("\\frac12", 0), EPS);
        assertEquals(3, eval("\\sqrt{x}", 9), EPS);
        assertEquals(-2, eval("\\sqrt[3]{x}", -8), EPS);
        assertEquals(5, eval("|x|", -5), EPS);
        assertEquals(1.0 / 3, eval("\\dfrac{1}{x}", 3), EPS);
    }

    @Test
    void operatorsAndBrackets() throws LatexParseException {
        assertEquals(12, eval("4\\times x", 3), EPS);
        assertEquals(2, eval("6\\div x", 3), EPS);
        assertEquals(20, eval("\\left(x+1\\right)\\cdot 5", 3), EPS);
        assertEquals(-9, eval("-x^2", 3), EPS);
        assertEquals(0.5, eval("x^{-1}", 2), EPS);
    }

    @Test
    void functionsAndConstants() throws LatexParseException {
        double x = 0.7;
        assertEquals(Math.sin(2 * x), eval("\\sin 2x", x), EPS);
        assertEquals(Math.sin(x) * Math.cos(x), eval("\\sin x\\cos x", x), EPS);
        assertEquals(Math.pow(Math.sin(x), 2), eval("\\sin^2 x", x), EPS);
        assertEquals(Math.pow(Math.sin(x), 2), eval("\\sin(x)^2", x), EPS);
        assertEquals(Math.asin(x), eval("\\sin^{-1}(x)", x), EPS);
        assertEquals(Math.exp(x), eval("e^{x}", x), EPS);
        assertEquals(Math.log(x), eval("\\ln x", x), EPS);
        assertEquals(3, eval("\\log_{2} x", 8), EPS);
        assertEquals(2, eval("\\log(x)", 100), EPS);
        assertEquals(2 * Math.PI * x, eval("2\\pi x", x), EPS);
        assertEquals(Math.cos(x), eval("cos(x)", x), EPS);
    }

    @Test
    void undefinedPointsAreNaN() throws LatexParseException {
        assertTrue(Double.isNaN(eval("\\sqrt{x}", -1)));
        assertTrue(Double.isInfinite(eval("\\frac{1}{x}", 0)));
    }

    @Test
    void rejectsWhatCannotBeGraphed() {
        assertThrows(LatexParseException.class, () -> parser.parse(""));
        assertThrows(LatexParseException.class, () -> parser.parse("y=ax+b"));
        assertThrows(LatexParseException.class, () -> parser.parse("8x+4=20"));
        assertThrows(LatexParseException.class, () -> parser.parse("\\text{Let }A"));
        assertThrows(LatexParseException.class, () -> parser.parse("\\frac{1}{"));
        assertThrows(LatexParseException.class, () -> parser.parse("(x+1"));
    }
}
