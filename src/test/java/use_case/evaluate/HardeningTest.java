package use_case.evaluate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardeningTest {
    @Test
    void bigAnswersShowRealDigitsOrAPowerOfTen() {
        assertEquals("= 123456123456", Calc.once("123456\\cdot 1000001="));
        assertEquals("= 1099511627776", Calc.once("2^{40}="));
        assertEquals("= 2.432902008×10^18", Calc.once("20!="));
        assertEquals("= 1×10^300", Calc.once("10^{300}="));
    }

    @Test
    void realTinyValuesAreNotZero() {
        assertEquals("= 1×10^-13", Calc.once("10^{-13}"));
        assertEquals("= 9.4×10^-14", Calc.once("e^{-30}"));
        assertEquals("= 8.9×10^-16", Calc.once("2^{-50}"));
        assertEquals("= 1×10^-15", Calc.once("1/10^{15}"));
        assertEquals("= 3×10^-14", Calc.once("3(10^{-14})"));
        assertEquals("= 1×10^-13", Calc.once("\\sin(10^{-13})"));
        assertEquals("= -1×10^-13", Calc.once("-10^{-13}"));
        assertEquals("false", Calc.once("10^{-13}=0"));
        assertEquals("true", Calc.once("10^{-13}>0"));
        // from 1e-12 up nothing changes
        assertEquals("= 0.000000000001", Calc.once("10^{-12}"));
        assertEquals("= 0.0000000000019", Calc.once("e^{-27}"));
        assertEquals("= -0.0000022", Calc.once("\\sin(10^{10}\\pi)"));
    }

    @Test
    void floatNoiseStillShowsAsZero() {
        assertEquals("= 0", Calc.once("0.1+0.2-0.3"));
        assertEquals("= 0", Calc.once("\\sin(\\pi)"));
        assertEquals("= 0", Calc.once("\\cos(\\pi/2)"));
        assertEquals("= 0", Calc.once("1-1/3-1/3-1/3"));
        assertEquals("= 0", Calc.once("(\\sqrt{2})^2-2"));
        assertEquals("= 0", Calc.once("\\tan(\\pi)"));
        assertEquals("= 0", Calc.once("\\sin\\pi"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to\\infty}\\frac{x}{e^x}"));
        assertEquals("= 0", Calc.once("\\sum_{n=1}^{\\infty}\\frac{1}{n^2}-\\frac{\\pi^2}{6}"));
        assertEquals("= (0, 0)", Calc.once("(1,2)-(1,2)"));
        Calc calc = new Calc();
        calc.show("f(x)=\\sin x");
        assertEquals("= 0", calc.show("f(\\pi)"));
        // roots keep the old rule for now
        assertEquals("x = 0", Calc.once("x=10^{-13}"));
        assertEquals("x = -0.000000000000001 or x = 0.000000000000001", Calc.once("x^2=10^{-30}"));
        assertEquals("-0.00001 < x < 0.00001", Calc.once("x^2<10^{-10}"));
    }

    @Test
    void tinyAnswersHaveNoTrailingZero() {
        assertEquals("= 0.00000006", Calc.once("5.99999\\cdot 10^{-8}"));
        assertEquals("= 0.00000006", Calc.once("6\\cdot 10^{-8}"));
        assertEquals("= 0.001", Calc.once("\\sum_{n=1}^{\\infty}\\frac{1}{(n+1000)^2}"));
        assertEquals("= 0.000001", Calc.once("\\lim_{x\\to 0}\\frac{\\sin(x)}{10^6x}"));
    }

    @Test
    void boundaryRightOnTheRangeEndStillAnswers() {
        assertEquals("x > 100", Calc.once("x>100"));
        assertEquals("x ≥ 100", Calc.once("x\\geq 100"));
        assertEquals("x < -100", Calc.once("x<-100"));
        assertEquals("x ≤ -100", Calc.once("x\\leq -100"));
        assertEquals("x ≠ 100", Calc.once("x\\neq 100"));
        assertEquals("x ≠ -100", Calc.once("x\\neq -100"));
        assertEquals("x < -100 or x > 100", Calc.once("|x|>100"));
        assertEquals("x ≤ -100 or x ≥ 100", Calc.once("x^2\\geq 10000"));
        // just past the end comes from the far scan now
        assertEquals("x > 100.001", Calc.once("x>100.001"));
        assertEquals("x ≥ 200", Calc.once("x\\geq 200"));
        // root on the end + endless ones after it
        assertEquals("Uncalculable — Too many solutions", Calc.once("\\sin(\\pi x)>0"));
        assertEquals("Uncalculable — Too many solutions", Calc.once("\\sin(\\pi x)\\neq 0"));
    }

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
