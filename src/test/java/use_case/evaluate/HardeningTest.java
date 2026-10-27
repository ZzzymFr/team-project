package use_case.evaluate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardeningTest {
    @Test
    void touchingRootsBetweenGridPointsAreFound() {
        assertEquals("x = 1.414213562", Calc.once("(x-\\sqrt{2})^2=0"));
        assertTrue(Calc.once("\\cos(x)=1").endsWith("(and 21 more)"), Calc.once("\\cos(x)=1"));
        assertEquals("x = 0", Calc.once("\\cos(x)=1\\land x>-1\\land x<1"));
        assertEquals("x = 1.570796327", Calc.once("\\sin(x)=1\\land x>0\\land x<3"));
        assertEquals("true", Calc.once("\\exists x\\in\\mathbb{R}, \\sin(x)=1"));
        assertEquals("false", Calc.once("\\forall x\\in\\mathbb{R}, \\sin(x)<1"));
        assertEquals("true", Calc.once("\\exists x\\in\\mathbb{R}, (x-\\sqrt{2})^2=0"));
        assertEquals("false", Calc.once("\\exists x\\in\\mathbb{R}, (x-\\sqrt{2})^2+10^{-12}=0"));
    }

    @Test
    void domainsOfIsolatedPointsGiveNoWrongInterval() {
        assertEquals("Uncalculable", Calc.once("\\sum_{i=1}^{n}i>10"));
        assertEquals("Uncalculable", Calc.once("x\\in\\mathbb{Z}"));
        assertEquals("Uncalculable", Calc.once("x!>100"));
        assertEquals("Uncalculable", Calc.once("\\sin(1000x)>0.9999"));
        assertEquals("x = 0", Calc.once("\\sqrt{-x^2}\\geq0"));
    }

    @Test
    void notEqualExcludesUndefinedPoints() {
        assertEquals("x < 0 or x > 0", Calc.once("\\frac{1}{x}\\neq0"));
        assertEquals("true for all x", Calc.once("x^2\\neq-1"));
        assertEquals("x ≠ 3", Calc.once("x\\neq3"));
        assertEquals("no solution", Calc.once("x\\neq x"));
        assertEquals("x > 0", Calc.once("\\sqrt{x}\\neq0"));
        assertEquals("x < 0 or 0 < x < 1 or x > 1", Calc.once("\\frac{1}{x}\\neq1"));
        assertEquals("x ≠ -2 and x ≠ 2", Calc.once("x^2\\neq4"));
    }

    @Test
    void powersOfFunctionNames() {
        assertEquals("= 1", Calc.once("\\sin^2(1)+\\cos^2(1)"));
        assertEquals("= 1", Calc.once("\\sin^{2}(1)+\\cos^2 1"));
        assertEquals("= 1", Calc.once("\\tan^2(\\frac{\\pi}{4})"));
        assertEquals("= 1", Calc.once("\\ln^2 e"));
        assertEquals("= 8", Calc.once("\\ln^{3}(e^2)"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, \\sin^2x+\\cos^2x=1"));
        assertEquals("Uncalculable", Calc.once("\\sin^{-1}(1)"));
    }

    @Test
    void roundingAtFarSamplesMakesNoBoundary() {
        assertEquals("true for all x", Calc.once("e^{-x^2}>0"));
        assertEquals("no solution", Calc.once("e^{-x^2}<0"));
        assertEquals("x > 0", Calc.once("x\\cdot e^{-x^2}>0"));
        assertEquals("Uncalculable", Calc.once("e^{-x^2}>0\\land x<50"));
        assertEquals("true for all x", Calc.once("e^{x}+1>e^{x}"));
        assertEquals("true for all x", Calc.once("x^{100}\\geq0"));
        assertEquals("no solution", Calc.once("x^{-10}\\leq0"));
        assertEquals("Uncalculable", Calc.once("x+10^{20}>10^{20}"));
    }

    @Test
    void lostValuesThatCannotChangeTheAnswerKeepIt() {
        assertEquals("x < 2.197224577", Calc.once("\\frac{1}{1+e^{-x}}<0.9"));
        assertEquals("x < -1 or x > 0", Calc.once("\\sum_{i=1}^{50}x^i>0"));
        assertEquals("x = 0", Calc.once("x^6\\leq0"));
        assertEquals("x ≥ 0", Calc.once("x^5\\geq0"));
    }
}
