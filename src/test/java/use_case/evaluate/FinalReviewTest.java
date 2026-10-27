package use_case.evaluate;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalReviewTest {
    private static final String UNCALCULABLE = "Uncalculable";

    // right answer or uncalc, never wrong
    private static void correctOrUncalculable(String correct, String input) {
        String answer = assertTimeout(Duration.ofSeconds(2), () -> Calc.once(input));
        assertTrue(answer.equals(correct) || answer.equals(UNCALCULABLE), input + " -> " + answer);
    }

    private static String quick(String input) {
        return assertTimeout(Duration.ofSeconds(2), () -> Calc.once(input));
    }

    @Test
    void uncalculablePointsAreNotFalse() {
        correctOrUncalculable("true", "\\forall a, \\sum_{n=1}^{\\infty}\\frac{1}{n\\ln(n+1)}>0");
        correctOrUncalculable("true", "\\forall a\\in\\{1\\}, \\lim_{x\\to\\infty}(\\ln x-10^{12})\\neq5");
        correctOrUncalculable("true for all x", "\\lim_{t\\to\\infty}(\\ln t-10^{12})+x>0");
        correctOrUncalculable("true for all x", "\\lim_{t\\to\\infty}(\\ln t-10^{12})+x\\neq0");
        assertEquals(UNCALCULABLE, quick("\\forall x, (x,1)+(1,2,3)\\neq(0,0)"));
        assertEquals(UNCALCULABLE, quick("\\exists x, (x,1)+(1,2,3)\\neq(0,0)"));
        assertEquals(UNCALCULABLE, quick("\\forall x, \\{x\\}+1\\neq2"));
        assertEquals(UNCALCULABLE, quick("(x,1)+(1,2,3)\\neq(0,0)"));
    }

    @Test
    void undefinedPointsStillFail() {
        assertEquals("false", quick("\\forall x\\in\\mathbb{R}, \\frac{1}{x}\\neq0"));
        assertEquals("x > 0", quick("\\frac{1}{x}>0"));
        assertEquals("x < 0 or x > 0", quick("\\frac{1}{x}\\neq0"));
        assertEquals("x ≥ 0", quick("\\sqrt{x}\\geq0"));
        assertEquals("n = 10", quick("\\sum_{i=1}^{n}i=55"));
    }

    @Test
    void overflowDoesNotDecideComparisons() {
        correctOrUncalculable("false", "200!=201!");
        correctOrUncalculable("false", "e^{1000}=e^{1001}");
        correctOrUncalculable("true", "e^{1000}<e^{1001}");
        correctOrUncalculable("true", "\\forall x, e^{1000x}\\geq e^{1000}\\Rightarrow x\\geq1");
        assertEquals("true", quick("\\infty=\\infty+1"));
        assertEquals("true", quick("\\lim_{x\\to0}\\frac{1}{x^2}=\\infty"));
        assertEquals("x > 0.1609437912", quick("e^{10x}>5"));
    }

    @Test
    void setAndVectorEquationsAreSolved() {
        assertEquals("x = 2", quick("\\{x\\}=\\{2\\}"));
        assertEquals("x = 2", quick("(x,1)=(2,1)"));
        assertEquals("true for all x", quick("(x,1)=(x,1)"));
        assertEquals("x = 1 or x = 2", quick("\\{1,2\\}\\cup\\{x\\}=\\{1,2\\}"));
        assertEquals("x = 2.505", quick("(x,1)=(2.505,1)"));
        assertEquals("no solution", quick("(x,1)=(2,3)"));
        assertEquals("x = 0 or x = 1", quick("\\{x,1\\}=\\{1,x^2\\}"));
        assertEquals("x = 2", quick("(x,x^2)=(2,4)"));
        assertEquals("true", quick("\\exists x, (x,1)=(200,1)"));
    }

    @Test
    void narrowTruthRegionsAreFound() {
        assertEquals("true", quick("\\exists x\\in\\mathbb{R}, x>50\\land x<50.001"));
        assertEquals("true", quick("\\exists x, x>5\\land x<5.0001"));
        assertEquals("true", quick("\\exists x, |x-500|<0.01"));
        assertEquals("true", quick("\\exists x, (x-1)(x-1.0001)<0"));
        assertEquals("false", quick("\\forall x, x\\leq0\\lor x\\geq0.00005"));
        assertEquals("true", quick("\\forall\\epsilon, \\epsilon>0\\Rightarrow\\exists\\delta, \\delta>0\\land\\delta<\\epsilon"));
        assertEquals("false", quick("\\exists x, |x-500|<-0.01"));
        assertEquals("true", quick("\\exists x, (x-1000)^2<0.0001"));
        assertEquals("false", quick("\\forall x, (x-1000)^2\\geq0.0001"));
        assertEquals("true", quick("\\exists x, x>5000\\land x<5000.001"));
        assertEquals("1 < x < 1.0001", quick("(x-1)(x-1.0001)<0"));
        assertEquals("x < 1 or x > 1.0001", quick("(x-1)(x-1.0001)>0"));
        // period below grid step, cant solve
        assertEquals(UNCALCULABLE, quick("\\sin(1000x)=1"));
        assertTrue(quick("\\sin(100x)=0.9999").startsWith("x = -99.9499117 or x = -99.94962885 or "));
    }

    @Test
    void derivativeIdentitiesAreNotNoise() {
        assertEquals("true for all x", quick("\\frac{d}{dx}e^x=e^x"));
        assertEquals("x > 0", quick("\\frac{d}{dx}|x|=1"));
        correctOrUncalculable("true", "\\forall x, \\frac{d}{dx}\\sin(x)=\\cos(x)");
        assertEquals("x > 0", quick("\\frac{d}{dx}|x|>0"));
        correctOrUncalculable("x > 0", "\\lim_{t\\to0}\\frac{|x+t|-|x|}{t}=1");
        Calc c = new Calc();
        c.show("f(x)=|x|");
        assertEquals("x > 0", c.show("f'(x)=1"));
        assertEquals("= 1", c.show("f'(0.0001)"));
        assertEquals("= undefined", c.show("f'(0)"));
        c.show("g(x)=|x-5|");
        assertEquals("= 1", c.show("g'(5.0002)"));
        c.show("h(x)=\\sqrt{x}");
        assertEquals("= 500", c.show("h'(0.000001)"));
        assertEquals("= 50", c.show("h'(0.0001)"));
        c.show("s(x)=\\sin(x)");
        assertEquals("= 0.936752128", c.show("s'(1000000)"));
        assertEquals("x = 0.6931471806", c.show("\\frac{d}{dx}e^{x}=2"));
        correctOrUncalculable("true", "\\forall x, \\frac{d}{dx}x^2=2x");
    }

    @Test
    void alwaysTrueNeedsMoreThanSixPoints() {
        String zeros = quick("\\sin(10\\pi x)=0");
        assertTrue(zeros.equals(UNCALCULABLE) || zeros.startsWith("x = -100 or x = -99.9 or "), zeros);
        String nonZeros = quick("\\sin(10\\pi x)\\neq0");
        assertTrue(!nonZeros.equals("no solution") && !nonZeros.startsWith("true"), nonZeros);
        correctOrUncalculable("x = 0", "x\\cdot(1+10^{-20})=x");
        correctOrUncalculable("x = 0", "x+10^{20}=10^{20}");
        assertEquals("true for all x", quick("x+0=x"));
        assertEquals("true for all x", quick("\\sin(x)^2+\\cos(x)^2=1"));
        assertEquals("true for all x", quick("(x+1)^4=x^4+4x^3+6x^2+4x+1"));
        // every grid pt is a root
        String gridRoots = quick("\\sin(100\\pi x)=0");
        assertTrue(gridRoots.equals(UNCALCULABLE) || gridRoots.startsWith("x = -100 or x = -99.99 or x = -99.98 or "),
                gridRoots);
        assertEquals("x = -100 or x = 100", quick("x^2=10000"));
        assertEquals("no solution", quick("e^{-x^2}=0"));
    }

    @Test
    void answersBeyondTheSearchRangeAreNotDenied() {
        assertEquals("x = 200", quick("x=200"));
        assertEquals("x = -200 or x = 200", quick("x^2=40000"));
        correctOrUncalculable("x > 200", "x>200");
        assertEquals(UNCALCULABLE, quick("\\sin(x)>0"));
        assertTrue(quick("\\sin(x)=0").endsWith("(and 53 more)"));
        assertEquals("x > 0", quick("x>0"));
        assertEquals("no solution", quick("x^2=-1"));
        assertEquals("true for all x", quick("x^2\\geq0"));
        assertEquals("x ≠ 200", quick("x\\neq200"));
        assertEquals("x = 1000", quick("\\sqrt{x-1000}=0"));
        assertEquals("no solution", quick("\\frac{1}{x-200}=0"));
        assertEquals("x = -300 or x = 200", quick("(x-200)(x+300)=0"));
        correctOrUncalculable("x = 1000", "e^{-x}(x-1000)=0");
        assertEquals(UNCALCULABLE, quick("|x-500|<0.01"));
        assertEquals(UNCALCULABLE, quick("\\sin(\\frac{x}{1000})>0"));
    }

    @Test
    void inclusionIsDecidedBeforeRounding() {
        assertEquals("x > 3.141592654", quick("\\sqrt{x-\\pi}>0"));
        assertEquals("x ≥ 3.141592654", quick("\\sqrt{x-\\pi}\\geq0"));
        assertEquals("x ≥ 0", quick("\\sqrt{x}>-1"));
        assertEquals("x ≤ -1.414213562 or x ≥ 1.414213562", quick("x^2\\geq2"));
    }

    @Test
    void domainEdgeAndTouchingRootsInQuantifiers() {
        assertEquals("true", quick("\\exists x, \\sqrt{x-1000}=0"));
        correctOrUncalculable("true", "\\exists x, e^{-x}(x-1000)=0");
        correctOrUncalculable("true", "\\exists x\\exists y, \\sin x+\\cos y=2");
        assertEquals("false", quick("\\exists x, e^{-x}=0"));
        assertEquals("true", quick("\\exists x, \\sqrt{x-1234}=0"));
        assertEquals("true", quick("\\exists x\\exists y, \\sin x+\\cos y=1.5"));
        assertEquals("false", quick("\\exists x\\exists y, x^2+y^2=-1"));
        assertEquals("false", quick("\\forall x, e^{-x}(x-1000)\\neq0"));
    }

    @Test
    void approximationWithNegativeLiterals() {
        assertEquals("true", quick("-3.14\\approx-\\pi"));
        assertEquals("false", quick("-3.15\\approx-\\pi"));
        assertEquals("false", quick("3.15\\approx\\pi"));
        assertEquals("true", quick("-\\pi\\approx-3.14"));
    }

    @Test
    void logarithmDomainIsNotCutAtOneHundredth() {
        correctOrUncalculable("x > 0", "e^{\\ln(x)}=x");
        correctOrUncalculable("x > 0", "\\ln(x^2)=2\\ln(x)");
        correctOrUncalculable("x > 0", "\\ln(x^2)\\geq2\\ln(x)");
        correctOrUncalculable("x > 0", "e^{\\ln(x)}\\geq x");
        assertEquals("x = 0", quick("x^6\\leq0"));
        assertEquals("x ≥ 0", quick("\\sqrt{x}^2=x"));
    }

    @Test
    void manyIntervalsAreCapped() {
        // keeps going past range -> uncalc (R31)
        String answer = quick("\\sin(100x)>0\\land|x|<50");
        assertTrue(answer.length() < 1000 && answer.matches(".* \\(and \\d+ more\\)"), answer);
    }

    @Test
    void plusMinusExclusionKeepsUndefinedPoints() {
        assertEquals("x ≠ -1 and x ≠ 0 and x ≠ 1", quick("\\frac{1}{x}\\neq\\pm1"));
        assertEquals("x ≠ -1 and x ≠ 1", quick("x\\neq\\pm1"));
    }
}
