package use_case.evaluate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardeningTest {
    @Test
    void touchingRootsBetweenGridPointsAreFound() {
        assertEquals("x = 1.41", Calc.once("(x-\\sqrt{2})^2=0"));
        assertTrue(Calc.once("\\cos(x)=1").endsWith("(and 21 more)"), Calc.once("\\cos(x)=1"));
        assertEquals("x = 0", Calc.once("\\cos(x)=1\\land x>-1\\land x<1"));
        assertEquals("x = 1.57", Calc.once("\\sin(x)=1\\land x>0\\land x<3"));
        assertEquals("true", Calc.once("\\exists x\\in\\mathbb{R}, \\sin(x)=1"));
        assertEquals("false", Calc.once("\\forall x\\in\\mathbb{R}, \\sin(x)<1"));
        assertEquals("true", Calc.once("\\exists x\\in\\mathbb{R}, (x-\\sqrt{2})^2=0"));
        assertEquals("false", Calc.once("\\exists x\\in\\mathbb{R}, (x-\\sqrt{2})^2+10^{-12}=0"));
    }

    @Test
    void touchingRootsAreSearchedOverTheWholeRange() {
        assertEquals("x = 50.42", Calc.once("\\sin(10x)=1\\land x>50\\land x<51"));
        assertEquals("true", Calc.once("\\exists x\\in\\mathbb{R}, \\sin(10x)=1\\land x>90"));
        assertTrue(Calc.once("\\sin(10x)=1").endsWith("(and 308 more)"), Calc.once("\\sin(10x)=1"));
        assertTrue(Calc.once("\\sin(x)=0").endsWith("(and 53 more)"), Calc.once("\\sin(x)=0"));
    }

    @Test
    void sidesEqualOnlyByLosingAValueGiveNoRoots() {
        assertEquals("no solution", Calc.once("e^{-x^2}=0"));
        assertEquals("no solution", Calc.once("2^{-x^2}=0"));
        assertEquals("no solution", Calc.once("\\frac{1}{e^{x^2}}=0"));
        assertEquals("no solution", Calc.once("e^{-x}=0"));
        assertEquals("no solution", Calc.once("e^{x}=0"));
        assertEquals("x = -6.79 or x = 6.79", Calc.once("e^{-x^2}=10^{-20}"));
        assertEquals("x = 0", Calc.once("x^{10}=0"));
        assertEquals("x = 1", Calc.once("(x-1)^2=0"));
        assertEquals("no solution", Calc.once("\\frac{1}{x}=0"));
        assertEquals("x = 100", Calc.once("x=100"));
        assertEquals("x ≥ 0", Calc.once("|x|=x"));
        assertEquals("x < 0", Calc.once("|x|\\neq x"));
    }

    @Test
    void domainsOfIsolatedPointsGiveNoWrongInterval() {
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\sum_{i=1}^{n}i>10")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("x\\in\\mathbb{Z}")));
        assertEquals("x > 4.89", Calc.once("x!>100"));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\sin(1000x)>0.9999")));
        assertEquals("x = 0", Calc.once("\\sqrt{-x^2}\\geq0"));
        assertEquals("x = 0", Calc.once("\\sqrt{-x^2}+1>0"));
        assertEquals("x = 0", Calc.once("\\sqrt{-x^2}<1"));
        assertEquals("x = 0", Calc.once("\\sqrt{x}+\\sqrt{-x}>-1"));
        assertEquals("x = 1", Calc.once("\\sqrt{x-1}+\\sqrt{1-x}<5"));
        assertEquals("x = 1 or x = 2 or x = 3", Calc.once("x\\in\\mathbb{Z}\\land x>0\\land x<4"));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("x\\in\\mathbb{Z}\\land x>0\\land x<20")));
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
        assertEquals("x < 1 or x > 1", Calc.once("\\frac{x^2-1}{x-1}\\neq2"));
    }

    @Test
    void powersOfFunctionNames() {
        assertEquals("= 1", Calc.once("\\sin^2(1)+\\cos^2(1)"));
        assertEquals("= 1", Calc.once("\\sin^{2}(1)+\\cos^2 1"));
        assertEquals("= 1", Calc.once("\\tan^2(\\frac{\\pi}{4})"));
        assertEquals("= 1", Calc.once("\\ln^2 e"));
        assertEquals("= 8", Calc.once("\\ln^{3}(e^2)"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, \\sin^2x+\\cos^2x=1"));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\sin^{-1}(1)")));
    }

    @Test
    void roundingAtFarSamplesMakesNoBoundary() {
        assertEquals("true for all x", Calc.once("e^{-x^2}>0"));
        assertEquals("no solution", Calc.once("e^{-x^2}<0"));
        assertEquals("x > 0", Calc.once("x\\cdot e^{-x^2}>0"));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("e^{-x^2}>0\\land x<50")));
        assertEquals("true for all x", Calc.once("e^{x}+1>e^{x}"));
        assertEquals("true for all x", Calc.once("x^{100}\\geq0"));
        assertEquals("no solution", Calc.once("x^{-10}\\leq0"));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("x+10^{20}>10^{20}")));
    }

    @Test
    void lostValuesThatCannotChangeTheAnswerKeepIt() {
        assertEquals("x < 2.2", Calc.once("\\frac{1}{1+e^{-x}}<0.9"));
        assertEquals("x < -1 or x > 0", Calc.once("\\sum_{i=1}^{50}x^i>0"));
        assertEquals("x = 0", Calc.once("x^6\\leq0"));
        assertEquals("x ≥ 0", Calc.once("x^5\\geq0"));
    }
}
