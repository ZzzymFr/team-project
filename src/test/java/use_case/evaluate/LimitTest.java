package use_case.evaluate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LimitTest {
    @Test
    void limitsAtInfinity() {
        assertEquals("true", Calc.once("\\lim_{x\\rightarrow\\infty}8x+3=\\infty"));
        assertEquals("true", Calc.once("\\lim_{x\\rightarrow\\infty}\\frac{1}{8x+3}=0"));
        assertEquals("= -∞", Calc.once("\\lim_{x\\rightarrow-\\infty}x^3"));
    }

    @Test
    void limitsAtAPoint() {
        assertEquals("= undefined", Calc.once("\\lim_{x\\rightarrow0}\\frac{1}{x}"));
        assertEquals("= 1", Calc.once("\\lim_{x\\to0^{+}}\\frac{|x|}{x}"));
        assertEquals("= -1", Calc.once("\\lim_{x\\to0^-}\\frac{|x|}{x}"));
        assertEquals("= 2", Calc.once("\\lim_{x\\rightarrow1}\\frac{x^2-1}{x-1}"));
    }

    @Test
    void solvingForTheTarget() {
        assertEquals("a = -2 or a = 2", Calc.once("\\lim_{x\\rightarrow a}x^2=4"));
    }
}
