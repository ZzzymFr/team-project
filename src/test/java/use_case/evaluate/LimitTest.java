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

    @Test
    void polesNearTheTarget() {
        assertEquals("= -2", Calc.once("\\lim_{x\\to10}\\frac{1}{x-10.5}"));
        assertEquals("= -10", Calc.once("\\lim_{x\\to2}\\frac{1}{x-2.1}"));
        assertEquals("= -0.2", Calc.once("\\lim_{x\\to100}\\frac{1}{x-105}"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to3}\\frac{x^2-9}{x-3.15}"));
        assertEquals("= -20", Calc.once("\\lim_{x\\to0}\\frac{1}{x-0.05}"));
        assertEquals("= -2", Calc.once("\\lim_{x\\to10^+}\\frac{1}{x-10.5}"));
    }

    @Test
    void slowLimitsAreRightOrUncalculable() {
        assertEquals("= 0", Calc.once("\\lim_{x\\to0^+}x^{0.01}"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to\\infty}\\frac{\\ln x}{x^{0.1}}"));
        assertEquals("Uncalculable", Calc.once("\\lim_{x\\to\\infty}\\frac{1}{\\ln x}"));
        assertEquals("Uncalculable", Calc.once("\\lim_{x\\to0^+}\\frac{1}{\\ln x}"));
        assertEquals("Uncalculable", Calc.once("\\lim_{x\\to\\infty}\\frac{\\ln x}{\\ln(2x)}"));
        assertEquals("Uncalculable", Calc.once("\\lim_{x\\to\\infty}\\frac{1}{\\sqrt{\\ln x}}"));
        assertEquals("Uncalculable", Calc.once("\\lim_{x\\to\\infty}\\ln(\\ln x)"));
        assertEquals("= ∞", Calc.once("\\lim_{x\\to\\infty}\\ln x"));
        assertEquals("= undefined", Calc.once("\\lim_{x\\to\\infty}\\sin x"));
    }

    @Test
    void domainAndSyntax() {
        assertEquals("= undefined", Calc.once("\\lim_{x\\to0}\\sqrt{x}"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to0^+}\\sqrt{x}"));
        assertEquals("Unexpected \"}\".", Calc.once("\\lim_{x\\to}x"));
    }

    @Test
    void samplesThatAreAllInfinite() {
        assertEquals("= ∞", Calc.once("\\lim_{x\\to\\infty}e^{1000x}"));
        assertEquals("= ∞", Calc.once("\\lim_{x\\to\\infty}e^{e^x}"));
        assertEquals("= ∞", Calc.once("\\lim_{x\\to0^+}e^{\\frac{100}{x}}"));
        assertEquals("= ∞", Calc.once("\\lim_{x\\to0}e^{\\frac{100}{x^2}}"));
    }

    @Test
    void oscillationAndLargeOffsets() {
        assertEquals("= undefined", Calc.once("\\lim_{x\\to\\infty}\\sin(\\ln x)"));
        assertEquals("= ∞", Calc.once("\\lim_{x\\to\\infty}x-1000000000000"));
        assertEquals("= -∞", Calc.once("\\lim_{x\\to\\infty}1000000000000-x"));
        assertEquals("= 1000000000000", Calc.once("\\lim_{x\\to\\infty}1000000000000+\\frac{1}{x}"));
    }
}
