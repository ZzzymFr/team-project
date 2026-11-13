package use_case.evaluate;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class SeriesTest {
    @Test
    void finiteSumsAndProducts() {
        assertEquals("= 14", Calc.once("\\sum_{i=1}^{3}i^2"));
        assertEquals("= 0", Calc.once("\\sum_{i=5}^{1}i"));
        assertEquals("= 1", Calc.once("\\prod_{i=5}^{1}i"));
    }

    @Test
    void infiniteSeries() {
        assertEquals("= ∞", Calc.once("\\sum_{n=1}^{\\infty}\\frac{1}{n}"));
        assertEquals("= undefined", Calc.once("\\sum_{n=0}^{\\infty}(-1)^n"));
    }

    @Test
    void undefinedAndUncalculable() {
        assertEquals("= undefined", Calc.once("\\sum_{n=0}^{3}\\frac{1}{n}"));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\sum_{i=1}^{2000000}i")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\sum_{i=1.5}^{3}i")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\sum x_i")));
    }

    @Test
    void solvingForTheUpperBound() {
        assertEquals("n = 10", Calc.once("\\sum_{i=1}^{n}i=55"));
    }

    @Test
    void nestedSumsStayWithinTheBudget() {
        assertTimeout(Duration.ofSeconds(2), () ->
                assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\sum_{i=1}^{1000000}\\sum_{j=1}^{1000000}1"))));
    }

    @Test
    void knownInfiniteSeries() {
        assertEquals("= 1.64", Calc.once("\\sum_{n=1}^{\\infty}\\frac{1}{n^2}"));
        assertEquals("= 0.69", Calc.once("\\sum_{n=1}^{\\infty}\\frac{(-1)^{n+1}}{n}"));
        assertEquals("= 2.72", Calc.once("\\sum_{n=0}^{\\infty}\\frac{1}{n!}"));
        assertEquals("= 3.68", Calc.once("\\prod_{n=1}^{\\infty}(1+\\frac{1}{n^2})"));
        assertEquals("= ∞", Calc.once("\\sum_{n=1}^{\\infty}\\frac{1}{\\sqrt{n}}"));
        assertEquals("= -∞", Calc.once("\\sum_{n=1}^{\\infty}-\\frac{1}{n}"));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\sum_{n=2}^{\\infty}\\frac{1}{n\\ln n}")));
        assertEquals("true", Calc.once("\\sum_{n=1}^{\\infty}\\frac{1}{n^2}=\\frac{\\pi^2}{6}"));
    }

    @Test
    void syntaxAndPlacement() {
        assertEquals("= 12", Calc.once("2\\sum_{i=1}^{3}i"));
        assertEquals("= 10", Calc.once("\\sum_{i=1}^{3}\\sum_{j=1}^{i}j"));
        assertEquals("= 6", Calc.once("\\sum_{i=1}^3 i"));
        assertEquals("= 6", Calc.once("\\sum_{x_1=1}^{3}x_1"));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\sum_{i=1}i")));
        assertEquals("Invalid formula — Missing \"}\"", Calc.once("\\sum_{i=1}^{3"));
        assertEquals("Invalid formula — Unexpected \"}\"", Calc.once("\\sum_{i=}^{3}i"));
    }

    @Test
    void laterTermsUsingTheIndexStayInTheBody() {
        assertEquals("= 20", Calc.once("\\sum_{n=1}^{3}n^2+n"));
        assertEquals("= 1", Calc.once("\\sum_{n=1}^{\\infty}\\frac{1}{n}-\\frac{1}{n+1}"));
        assertEquals("= 2.85", Calc.once("\\sum_{n=1}^{\\infty}\\frac{1}{n^2}+\\frac{1}{n^3}"));
        assertEquals("= 0.69", Calc.once("\\sum_{n=1}^{\\infty}\\frac{1}{2n-1}-\\frac{1}{2n}"));
        assertEquals("= 15", Calc.once("\\sum_{n=1}^{3}n+1+n"));
        assertEquals("= 13", Calc.once("\\sum_{n=1}^{3}n+n+1"));
        assertEquals("= 48", Calc.once("\\prod_{n=1}^{3}n+n"));
        assertEquals("= 56", Calc.once("\\sum_{n=1}^{10}n+1"));
        assertEquals("= 20", Calc.once("\\sum_{n=1}^{3}(n^2+n)"));
        assertEquals("= 36", Calc.once("\\sum_{k=1}^{3}\\sum_{j=1}^{3}kj"));
        assertEquals("= 0", Calc.once("\\sum_{n=1}^{\\infty}\\frac{1}{n^2}-\\frac{\\pi^2}{6}"));
        assertEquals("x = 2", Calc.once("\\sum_{n=1}^{3}x=6"));
        assertEquals("x = 10", Calc.once("\\sum_{n=1}^{x}n=55"));
        assertEquals("true", Calc.once("\\sum_{n=1}^{10}n=55"));
    }

    @Test
    void anInnerIndexOfTheSameNameIsNotAUse() {
        assertEquals("= 9", Calc.once("\\sum_{n=1}^{2}n+\\sum_{n=1}^{3}n"));
        assertEquals("= 21", Calc.once("\\sum_{n=1}^{3}1+\\sum_{k=1}^{3}n"));    }

    @Test
    void functionsDefinedBySeries() {
        Calc calc = new Calc();
        assertEquals("Defined f(x) = \\sum_{n=0}^{\\infty}\\frac{x^n}{n!}",
                calc.show("f(x)=\\sum_{n=0}^{\\infty}\\frac{x^n}{n!}"));
        assertEquals("= 7.39", calc.show("f(2)"));
    }

    @Test
    void slowlyShrinkingTermsAreNotDivergence() {
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\sum_{n=0}^{\\infty}0.999999^n")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\sum_{n=0}^{\\infty}(-0.999999)^n")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\sum_{n=1}^{\\infty}\\frac{1}{(n+3\\cdot10^5)^2}")));
    }

    @Test
    void anUpperBoundThatBecomesInfinityIsRounded() {
        Calc calc = new Calc();
        calc.show("g(n)=\\sum_{i=1}^{n}\\frac{1}{i^2}");
        assertEquals("= 1.64", calc.show("g(\\infty)"));
        assertEquals("= 1.25", calc.show("g(2)"));
    }

    @Test
    void onlyAnEqualsOutsideBracesStartsAnEquation() {
        assertEquals("Invalid formula — Only one \"=\" allowed", Calc.once("f(x)=\\sum_{i=1}^{3}i=6"));
        Calc calc = new Calc();
        assertEquals("Defined f(x) = \\sum_{i=1}^{3}i", calc.show("f(x)=\\sum_{i=1}^{3}i"));
        assertEquals("= 6", calc.show("f(0)"));
    }
}
