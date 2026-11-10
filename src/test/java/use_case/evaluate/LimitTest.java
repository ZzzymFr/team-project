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
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\lim_{x\\to\\infty}\\frac{1}{\\ln x}")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\lim_{x\\to0^+}\\frac{1}{\\ln x}")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\lim_{x\\to\\infty}\\frac{\\ln x}{\\ln(2x)}")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\lim_{x\\to\\infty}\\frac{1}{\\sqrt{\\ln x}}")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\lim_{x\\to\\infty}\\ln(\\ln x)")));
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
    void roundingInsideTheSamplesStaysInsideTheLimit() {
        assertEquals("x = 1", Calc.once("\\lim_{t\\to0}(10^{20}+t)x=10^{20}"));
        assertEquals("x = 1", Calc.once("\\lim_{t\\to0}10^{-20}t+x=1"));
        assertEquals("true", Calc.once("\\lim_{x\\to\\infty}\\frac{x}{x+10^{8}}=1"));
        assertEquals("x > 0", Calc.once("\\lim_{t\\to0}\\frac{|x+t|-|x|}{t}=1"));
    }

    @Test
    void oscillationAndLargeOffsets() {
        assertEquals("= undefined", Calc.once("\\lim_{x\\to\\infty}\\sin(\\ln x)"));
        assertEquals("= ∞", Calc.once("\\lim_{x\\to\\infty}x-1000000000000"));
        assertEquals("= -∞", Calc.once("\\lim_{x\\to\\infty}1000000000000-x"));
        assertEquals("= 1000000000000", Calc.once("\\lim_{x\\to\\infty}1000000000000+\\frac{1}{x}"));
    }

    @Test
    void dampedOscillationsHaveALimit() {
        assertEquals("= 0", Calc.once("\\lim_{x\\to0^+}\\sqrt{x}\\cos(\\ln x)"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to0^+}\\sqrt{x}\\sin(\\ln x)"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to0^+}x^{0.2}\\cos(\\ln x)"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to\\infty}\\frac{\\sin(\\ln x)}{\\sqrt{x}}"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to\\infty}\\frac{\\cos(\\ln x)}{x^{0.3}}"));
        assertEquals("= 1", Calc.once("\\lim_{x\\to0^+}1+\\sqrt{x}\\cos(\\ln x)"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to\\infty}\\frac{\\sin(\\ln x)}{x}"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to0}x\\sin(\\frac{1}{x})"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to\\infty}\\frac{\\sin x}{x}"));
    }

    @Test
    void slowDampedOscillationsAreUncalculable() {
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\lim_{x\\to\\infty}\\frac{\\sin x}{\\sqrt{x}}")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\lim_{x\\to0^+}\\sqrt{x}\\sin(\\frac{1}{x})")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\lim_{x\\to\\infty}\\frac{\\sin(\\ln x)}{\\ln x}")));
    }

    @Test
    void earlyEstimateIsNotTheLimit() {
        assertEquals("= 1", Calc.once("\\lim_{x\\to\\infty}\\frac{x^3}{x^3+10^{18}}"));
        assertEquals("= 1", Calc.once("\\lim_{x\\to\\infty}\\frac{x}{x+10^{8}}"));
        assertEquals("= 1", Calc.once("\\lim_{x\\to\\infty}\\frac{x}{x+10^{9}}"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to 0}\\frac{x}{x+10^{-8}}"));
    }

    @Test
    void bigConstantsAtInfinity() {
        assertEquals("= 1", Calc.once("\\lim_{x\\to\\infty}\\frac{x}{x+10^{10}}"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to\\infty}\\frac{10^{12}}{x+10^{12}}"));
        assertEquals("= 1", Calc.once("\\lim_{x\\to\\infty}\\frac{x^2}{x^2+10^{20}}"));
        assertEquals("= 2", Calc.once("\\lim_{x\\to\\infty}\\frac{2x+10^{11}}{x+10^{11}}"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to\\infty}e^{-x/10^{12}}"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to\\infty}\\frac{1}{x-10^{12}}"));
        assertEquals("= ∞", Calc.once("\\lim_{x\\to\\infty}(\\ln x-10^{12})"));
    }

    @Test
    void constantThatSwallowsXIsNotTheLimit() {
        assertEquals("= ∞", Calc.once("\\lim_{x\\to\\infty}(x-10^{20})"));
        assertEquals("= -∞", Calc.once("\\lim_{x\\to\\infty}(10^{20}-x)"));
        assertEquals("= ∞", Calc.once("\\lim_{x\\to\\infty}(x-10^{100})"));
        assertEquals("= -1×10^20", Calc.once("\\lim_{x\\to 3}(x-10^{20})"));
        assertEquals("= 1000000000000", Calc.once("\\lim_{x\\to\\infty}1000000000000+\\frac{1}{x}"));
    }

    @Test
    void floorStepsDontFakeALimit() {
        assertEquals("= 1", Calc.once("\\lim_{x\\to\\infty}\\frac{\\lfloor x\\rfloor}{x}"));
        assertEquals("= 1", Calc.once("\\lim_{x\\to\\infty}\\frac{\\lceil x\\rceil}{x}"));
        assertEquals("= 1", Calc.once("\\lim_{x\\to 0^+}x\\lfloor\\frac{1}{x}\\rfloor"));
        assertEquals("= 1.57", Calc.once("\\lim_{x\\to\\infty}\\arctan x"));
        assertEquals("= undefined", Calc.once("\\lim_{x\\to\\infty}(x-\\lfloor x\\rfloor)"));
    }

    @Test
    void tinyConstantsAtZero() {
        assertEquals("= 0", Calc.once("\\lim_{x\\to 0}\\frac{x^2}{x^2+10^{-20}}"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to 0}\\frac{x}{x+10^{-12}}"));
        // -10^{-12}/x -> -inf so e^... -> 0
        assertEquals("= 0", Calc.once("\\lim_{x\\to 0^+}e^{-10^{-12}/x}"));
    }

    @Test
    void bigConstantsThatAlreadyWorked() {
        assertEquals("= 1", Calc.once("\\lim_{x\\to\\infty}\\frac{x}{x+1000000}"));
        assertEquals("= 1000000", Calc.once("\\lim_{x\\to\\infty}\\frac{1000000x+1}{x+1000000}"));
        assertEquals("= 1", Calc.once("\\lim_{x\\to\\infty}\\frac{x+10^6}{x}"));
        assertEquals("= ∞", Calc.once("\\lim_{x\\to\\infty}(x-10^{12})"));
        assertEquals("= ∞", Calc.once("\\lim_{x\\to\\infty}\\frac{x}{10^{10}}"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to\\infty}\\frac{10^{100}}{x}"));
        assertEquals("= ∞", Calc.once("\\lim_{x\\to\\infty}\\frac{x}{10^{100}}"));
        assertEquals("= 500000", Calc.once("\\lim_{x\\to\\infty}(\\sqrt{x^2+10^6x}-x)"));
        assertEquals("= 2000", Calc.once("\\lim_{x\\to 1000}\\frac{x^2-1000000}{x-1000}"));
        assertEquals("= 200000000", Calc.once("\\lim_{x\\to 10^8}\\frac{x^2-10^{16}}{x-10^8}"));
        assertEquals("= 1000000", Calc.once("\\lim_{x\\to 0}\\frac{\\sin(10^6x)}{x}"));
    }

    @Test
    void exponentialsThatOverflowFarOut() {
        assertEquals("= 1", Calc.once("\\lim_{x\\to\\infty}\\frac{e^x}{e^x+1}"));
        assertEquals("= 1", Calc.once("\\lim_{x\\to\\infty}\\frac{e^x-e^{-x}}{e^x+e^{-x}}"));
        assertEquals("= 1", Calc.once("\\lim_{x\\to\\infty}\\frac{e^{2x}}{e^{2x}+1}"));
        assertEquals("= 1", Calc.once("\\lim_{x\\to\\infty}\\frac{2^x}{2^x+1}"));
        assertEquals("= 1", Calc.once("\\lim_{x\\to\\infty}\\frac{3^x+1}{3^x-1}"));
        assertEquals("= 1", Calc.once("\\lim_{x\\to\\infty}\\frac{e^x+x}{e^x-x}"));
        assertEquals("= 1", Calc.once("\\lim_{x\\to\\infty}\\frac{e^x+1}{e^x}"));
        assertEquals("= 1", Calc.once("\\lim_{x\\to\\infty}\\frac{\\ln(1+e^x)}{x}"));
        assertEquals("= 1", Calc.once("\\lim_{x\\to\\infty}\\frac{\\sqrt{e^{2x}+1}}{e^x}"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to -\\infty}\\frac{e^x}{e^x+1}"));
        assertEquals("= 1", Calc.once("\\lim_{x\\to\\infty}\\frac{1}{1+e^{-x}}"));
        assertEquals("= 1", Calc.once("\\lim_{x\\to\\infty}\\tanh x"));
        assertEquals("= 0.5", Calc.once("\\lim_{x\\to\\infty}\\frac{\\cosh x}{e^x}"));
        assertEquals("= 1.57", Calc.once("\\lim_{x\\to\\infty}\\arctan(e^x)"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to\\infty}\\frac{e^x}{x!}"));
        assertEquals("= ∞", Calc.once("\\lim_{x\\to\\infty}\\frac{e^x}{x^2}"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to\\infty}\\frac{e^x}{e^{2x}}"));
        assertEquals("= -∞", Calc.once("\\lim_{x\\to\\infty}e^x-e^{2x}"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to\\infty}\\frac{e^x}{e^x+e^{2x}}"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to\\infty}0.5^x"));
        assertEquals("= ∞", Calc.once("\\lim_{x\\to\\infty}1.5^x"));
        assertEquals("= 0", Calc.once("\\lim_{n\\to\\infty}\\frac{2^n}{n!}"));
        assertEquals("= 1", Calc.once("\\lim_{n\\to\\infty}\\sqrt[n]{n}"));
        assertEquals("= 1", Calc.once("\\lim_{x\\to\\infty}\\frac{e^x}{e^{x}+x^{10}}"));
    }

    @Test
    void nothingCleanFarOutLeavesTheNormalAnswer() {
        assertEquals("= undefined", Calc.once("\\lim_{x\\to 0}\\cot x"));
        assertEquals("= ∞", Calc.once("\\lim_{x\\to 0^+}\\cot x"));
        assertEquals("= ∞", Calc.once("\\lim_{x\\to 0}\\csc^2 x"));
        // solver asks this many times, far out has to stay cheap
        assertEquals("no solution", Calc.once("\\lim_{t\\to 0}\\cot(t)+x=0"));
    }

    @Test
    void polesAtZeroWithBothSigns() {
        assertEquals("= undefined", Calc.once("\\lim_{x\\to 0}\\frac{\\sin x}{x^2}"));
        assertEquals("= undefined", Calc.once("\\lim_{x\\to 0}\\frac{1}{x^3}"));
        assertEquals("= undefined", Calc.once("\\lim_{x\\to 0}\\frac{\\tan x}{x^2}"));
        assertEquals("= undefined", Calc.once("\\lim_{x\\to 0}\\frac{\\cos x}{x}"));
        assertEquals("= undefined", Calc.once("\\lim_{x\\to 0}\\frac{e^x}{x}"));
        assertEquals("= undefined", Calc.once("\\lim_{x\\to 0}\\frac{1}{\\sin x}"));
        assertEquals("= undefined", Calc.once("\\lim_{x\\to 0}\\frac{x+1}{x}"));
        assertEquals("= undefined", Calc.once("\\lim_{x\\to 1}\\frac{x}{x-1}"));
        assertEquals("= ∞", Calc.once("\\lim_{x\\to 0^+}\\frac{\\sin x}{x^2}"));
        assertEquals("= ∞", Calc.once("\\lim_{x\\to 0}\\frac{\\sin x}{x^3}"));
        assertEquals("= ∞", Calc.once("\\lim_{x\\to 0}\\frac{\\cos x}{x^2}"));
        assertEquals("= ∞", Calc.once("\\lim_{x\\to 0}\\frac{1}{x^4}"));
        assertEquals("= ∞", Calc.once("\\lim_{x\\to 0}\\frac{|x|}{x^2}"));
        assertEquals("= -∞", Calc.once("\\lim_{x\\to 0}(\\frac{1}{x}-\\frac{1}{x^2})"));
        assertEquals("= -∞", Calc.once("\\lim_{x\\to 0}\\ln|x|"));
        assertEquals("= -∞", Calc.once("\\lim_{x\\to 0^-}\\frac{1}{x^3}"));
    }

    @Test
    void textbookLimitsStayPut() {
        assertEquals("= 1", Calc.once("\\lim_{x\\to 0}\\frac{\\sin x}{x}"));
        assertEquals("= 0.5", Calc.once("\\lim_{x\\to 0}\\frac{1-\\cos x}{x^2}"));
        assertEquals("= 1/6", Calc.once("\\lim_{x\\to 0}\\frac{x-\\sin x}{x^3}"));
        assertEquals("= 2.72", Calc.once("\\lim_{x\\to\\infty}(1+\\frac{1}{x})^x"));
        assertEquals("= 0.5", Calc.once("\\lim_{x\\to\\infty}\\frac{x^2+1}{2x^2-3}"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to\\infty}\\frac{x}{e^x}"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to\\infty}\\frac{\\ln x}{x}"));
        assertEquals("= ∞", Calc.once("\\lim_{x\\to 0^+}\\frac{1}{x}"));
        assertEquals("= -∞", Calc.once("\\lim_{x\\to 0^-}\\frac{1}{x}"));
        assertEquals("= ∞", Calc.once("\\lim_{x\\to 0}\\frac{1}{x^2}"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to 0^+}x\\ln x"));
        assertEquals("= -∞", Calc.once("\\lim_{x\\to 0^+}\\ln x"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to -\\infty}e^x"));
        assertEquals("= ∞", Calc.once("\\lim_{x\\to\\infty}x^2"));
        assertEquals("= -∞", Calc.once("\\lim_{x\\to\\infty}-x^2"));
        assertEquals("= ∞", Calc.once("\\lim_{x\\to\\infty}\\frac{2^x}{x^{10}}"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to\\infty}\\frac{x^{10}}{2^x}"));
        assertEquals("= 1", Calc.once("\\lim_{x\\to\\infty}x^{1/x}"));
        assertEquals("= ∞", Calc.once("\\lim_{x\\to\\infty}x^{0.001}"));
        assertEquals("= -1.36", Calc.once("\\lim_{x\\to\\infty}x((1+\\frac{1}{x})^{x}-e)"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to\\infty}e^{-x}\\sin x"));
        assertEquals("= 1/6", Calc.once("\\lim_{x\\to 0}\\frac{e^x-1-x-x^2/2}{x^3}"));
        assertEquals("= -1/3", Calc.once("\\lim_{x\\to 0}(\\frac{1}{x^2}-\\frac{1}{\\sin^2 x})"));
        assertEquals("= 0.61", Calc.once("\\lim_{x\\to 0}(\\cos x)^{1/x^2}"));
        assertEquals("= undefined", Calc.once("\\lim_{x\\to 2}\\frac{1}{(x-2)^3}"));
        assertEquals("= undefined", Calc.once("\\lim_{x\\to 0}e^{1/x}"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to 0^-}e^{1/x}"));
        assertEquals("= 0", Calc.once("\\lim_{x\\to 0}e^{-1/x^2}"));
    }

    @Test
    void undampedOscillationsHaveNoLimit() {
        assertEquals("= undefined", Calc.once("\\lim_{x\\to\\infty}\\cos x"));
        assertEquals("= undefined", Calc.once("\\lim_{x\\to0}\\sin(\\frac{1}{x})"));
        assertEquals("= undefined", Calc.once("\\lim_{x\\to\\infty}x\\sin x"));
    }
}
