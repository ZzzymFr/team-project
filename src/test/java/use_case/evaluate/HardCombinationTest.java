package use_case.evaluate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// hard mixes of tokeniser.json tokens, no integrals
class HardCombinationTest {
    @Test
    void arithmeticMixes() {
        assertEquals("= -9.5", Calc.once("\\frac{3!^{2}}{\\sqrt[3]{-27}}+|-5|\\times2^{-1}"));
        assertEquals("= 512", Calc.once("2^3^2"));
        assertEquals("= -4", Calc.once("-2^2"));
        assertEquals("= 0.25", Calc.once("\\sqrt{\\frac{1}{4}}\\cdot50\\%"));
        assertEquals("= 7", Calc.once("\\log_{2}{1024}-\\ln e^{3}"));
        assertEquals("= 1", Calc.once("\\sin^2(\\frac{\\pi}{7})+\\cos^2(\\frac{\\pi}{7})"));
        assertEquals("= 10", Calc.once("\\frac{5!}{3!\\cdot2!}"));
        assertEquals("= 0.87", Calc.once("2\\sin\\frac{\\pi}{6}\\cos\\frac{\\pi}{6}"));
        assertEquals("= 4", Calc.once("\\ln(e^{2})\\cdot\\log_{3}{81}\\div2"));
        assertEquals("= 1.2", Calc.once("\\frac{1}{\\frac{1}{2}+\\frac{1}{3}}"));
        assertEquals("= 130", Calc.once("20\\%\\times50+5!"));
        assertEquals("= 6 or 2 or 0 or -4", Calc.once("1\\pm2\\pm3"));
        assertEquals("true", Calc.once("\\frac{22}{7}\\approx\\pi"));
        assertEquals("= 1", Calc.once("\\sin 2\\pi+1"));
    }

    @Test
    void infinityAndUndefinedMixes() {
        assertEquals("= undefined", Calc.once("\\tan(\\frac{\\pi}{2})"));
        assertEquals("= undefined", Calc.once("0\\cdot\\infty"));
        assertEquals("= undefined", Calc.once("\\infty-\\infty"));
        assertEquals("= -∞", Calc.once("-\\infty\\cdot2"));
        assertEquals("= 0", Calc.once("\\frac{1}{\\infty}"));
        assertEquals("= 0", Calc.once("e^{-\\infty}"));
        assertEquals("= 3.32", Calc.once("(2.5)!"));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\frac{10^{200}\\cdot10^{200}}{10^{300}}")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("171!")));
    }

    @Test
    void logicMixes() {
        assertEquals("false", Calc.once("(3<5)\\land\\neg(2>4)\\Rightarrow\\bot"));
        assertEquals("true", Calc.once("\\top\\oplus\\top\\oplus\\top"));
        assertEquals("true", Calc.once("\\neg\\top\\lor(1+1=2)\\Leftrightarrow(2\\neq3)"));
        assertEquals("true", Calc.once("1<2<3<4\\land4>3>2"));
        assertEquals("true", Calc.once("\\bot\\Rightarrow\\bot\\Rightarrow\\bot"));
        assertEquals("false", Calc.once("(\\bot\\Rightarrow\\bot)\\Rightarrow\\bot"));
        assertEquals("1 < x < 5 or x = 10", Calc.once("x>1\\land x<5\\lor x=10"));
        assertEquals("x ≤ -2 or x ≥ 2", Calc.once("\\neg(x^2<4)"));
        assertEquals("0 < x ≤ 2", Calc.once("(x>0)\\oplus(x>2)"));
        assertEquals("true for all x", Calc.once("x^2-5x+6<0\\Leftrightarrow2<x\\land x<3"));
        assertEquals("x ≤ 3 or x > 5", Calc.once("x>3\\Rightarrow x>5"));
    }

    @Test
    void setMixes() {
        assertEquals("= 3", Calc.once("|\\{1,1,2,\\sqrt{4},3\\}|"));
        assertEquals("= {2, 3}", Calc.once("\\{3,1,2\\}\\cap\\{2,3,9\\}"));
        assertEquals("true", Calc.once("\\sqrt{9}\\in\\mathbb{N}"));
        assertEquals("true", Calc.once("\\{1,2\\}\\subset\\{2,1\\}\\land\\{\\}\\subset\\mathbb{N}"));
        assertEquals("true", Calc.once("\\mathbb{N}\\subset\\mathbb{Z}\\subset\\mathbb{Q}\\subset\\mathbb{R}"));
        assertEquals("false", Calc.once("(1,2.5)\\in\\mathbb{Z}^2"));
        assertEquals("true", Calc.once("5\\notin\\{1,2\\}\\cup\\{3\\}"));
        assertEquals("x = 2 or x = 3", Calc.once("x\\in\\{1,2,3\\}\\land x>1"));
        assertEquals("x = 2", Calc.once("\\{x\\}=\\{2\\}"));
        assertEquals("= 6", Calc.once("|\\{1,2\\}\\cup\\{2,3\\}|!"));
        assertEquals("= 3", Calc.once("\\log_{|\\{1,2\\}|}(8)"));
    }

    @Test
    void vectorMixes() {
        assertEquals("= 15", Calc.once("\\|(3,4)\\|+|(6,8)|"));
        assertEquals("= (0, 1)", Calc.once("2(1,2)-\\frac{(4,6)}{2}"));
        assertEquals("= (2, -1, 0)", Calc.once("\\langle1,2,3\\rangle\\times\\langle0,0,1\\rangle"));
        assertEquals("= 0", Calc.once("((1,2,3)\\times(4,5,6))\\cdot(1,2,3)"));
        assertEquals("= 22", Calc.once("(1,2)\\cdot(3,4)\\times2"));
        assertEquals("x = -3 or x = 3", Calc.once("|(x,4)|=5"));
        assertEquals("x = 3", Calc.once("(x,2x)=(3,6)"));
        assertEquals("= 48", Calc.once("\\prod_{k=1}^{3}(1,k)\\cdot(k,1)"));
        assertEquals("= 6", Calc.once("\\lim_{x\\rightarrow2}(x,x^2)\\cdot(1,1)"));
        assertEquals("true", Calc.once("(1<2)\\land(\\{1\\}\\subset\\{1,2\\})\\land((1,0)\\perp(0,1))"));
    }

    @Test
    void functionAndDerivativeMixes() {
        Calc calc = new Calc();
        calc.show("f(x)=x^3-2x");
        calc.show("g(x)=\\sin(x)e^{x}");
        calc.show("h(x,y)=x^2y+\\sin(y)");
        calc.show("T(x,y)=3x+4y");
        assertEquals("= 56", calc.show("f(f(2))"));
        assertEquals("= 0", calc.show("f^{(4)}(1)"));
        assertEquals("x = -0.82 or x = 0.82", calc.show("f'(x)=0"));
        assertEquals("= 2", calc.show("g''(0)"));
        assertEquals("= 5", calc.show("\\frac{\\partial h}{\\partial y}(2,0)"));
        assertEquals("= 12.57", calc.show("\\frac{\\partial h}{\\partial x}(2,\\pi)"));
        assertEquals("= 10", calc.show("\\frac{\\partial^2 h}{\\partial x^2}(3,5)"));
        assertEquals("= 29", calc.show("T(T(1,1),2)"));
        assertEquals("= 21", calc.show("f'(2)+T(1,2)\\cdot g'(0)"));
        assertEquals("true", calc.show("\\forall x\\in\\mathbb{R}, f''(x)=6x"));
        assertEquals("-1.41 < x < 0 or x > 1.41", calc.show("f(x)>0"));
        assertEquals("x = 2", calc.show("T(x,1)=10"));
        assertEquals("x = 3.14", calc.show("\\frac{d^2}{dx^2}\\sin(x)=0\\land x>0\\land x<4"));
        assertEquals("x = -5/3 or x = 1", calc.show("\\frac{d}{dx}\\sum_{n=1}^{3}x^n=6"));
    }

    @Test
    void cornerDerivatives() {
        Calc calc = new Calc();
        calc.show("k(x)=\\sqrt{x}");
        calc.show("p(x)=|x|");
        calc.show("s(x)=x^{10}");
        assertEquals("= 0.25", calc.show("k'(4)"));
        assertEquals("= undefined", calc.show("k'(0)"));
        assertEquals("= undefined", calc.show("p'(0)"));
        assertEquals("= -1", calc.show("p'(-3)"));
        assertEquals("= 322560", calc.show("s^{(4)}(2)"));
    }

    @Test
    void hardLimits() {
        assertEquals("= 0.5", Calc.once("\\lim_{x\\rightarrow0}\\frac{\\tan x-\\sin x}{x^3}"));
        assertEquals("= 0.5", Calc.once("\\lim_{x\\rightarrow\\infty}(\\sqrt{x^2+x}-x)"));
        assertEquals("= 1", Calc.once("\\lim_{x\\rightarrow0^{+}}x^{x}"));
        assertEquals("= 7.39", Calc.once("\\lim_{x\\rightarrow\\infty}(1+\\frac{2}{x})^{x}"));
        assertEquals("= 7.39", Calc.once("\\lim_{x\\rightarrow\\infty}(\\frac{x+1}{x-1})^{x}"));
        assertEquals("= 0", Calc.once("\\lim_{x\\rightarrow0}x\\sin(\\frac{1}{x})"));
        assertEquals("= undefined", Calc.once("\\lim_{x\\rightarrow0}\\sin(\\frac{1}{x})"));
        assertEquals("= 5/3", Calc.once("\\lim_{x\\rightarrow0}\\frac{\\sin(5x)}{\\sin(3x)}"));
        assertEquals("= 1", Calc.once("\\lim_{x\\rightarrow\\frac{\\pi}{2}}(\\frac{\\pi}{2}-x)\\tan x"));
        assertEquals("= 2", Calc.once("\\lim_{x\\rightarrow1}\\lim_{y\\rightarrow2}xy"));
        assertEquals("= 2.72", Calc.once("\\lim_{x\\rightarrow0}(1+x)^{\\frac{1}{x}}"));
        assertEquals("= 0", Calc.once("\\lim_{x\\rightarrow0}\\frac{x^2\\sin(\\frac{1}{x})}{\\sin x}"));
        assertEquals("= 0", Calc.once("\\lim_{x\\rightarrow0^{+}}\\frac{e^{-\\frac{1}{x}}}{x^{5}}"));
        assertEquals("= -0.5", Calc.once("\\lim_{x\\rightarrow0}\\frac{\\ln(\\cos x)}{x^2}"));
        assertEquals("= -1", Calc.once("\\lim_{x\\rightarrow0^{-}}\\frac{|x|}{x}\\cdot\\lim_{x\\rightarrow0^{+}}\\frac{|x|}{x}"));
        assertEquals("= 10", Calc.once("\\sum_{k=1}^{4}\\lim_{x\\rightarrow0}\\frac{\\sin(kx)}{x}"));
    }

    @Test
    void hardSeries() {
        assertEquals("= 0.69", Calc.once("\\sum_{n=1}^{\\infty}\\frac{(-1)^{n+1}}{n}"));
        assertEquals("= 0.79", Calc.once("\\sum_{n=0}^{\\infty}\\frac{(-1)^n}{2n+1}"));
        assertEquals("= 2", Calc.once("\\sum_{n=1}^{\\infty}\\frac{n}{2^n}"));
        assertEquals("= 0.5", Calc.once("\\prod_{k=2}^{\\infty}(1-\\frac{1}{k^2})"));
        assertEquals("= 3.68", Calc.once("\\prod_{n=1}^{\\infty}(1+\\frac{1}{n^2})"));
        assertEquals("= 220", Calc.once("\\sum_{i=1}^{10}\\sum_{j=1}^{i}j"));
        assertEquals("= 7.39", Calc.once("\\sum_{n=0}^{\\infty}\\frac{2^n}{n!}"));
        assertEquals("= 0.5", Calc.once("\\sum_{n=0}^{\\infty}\\frac{(-1)^n}{(2n+1)!}(\\frac{\\pi}{6})^{2n+1}"));
        assertEquals("= -0.6", Calc.once("\\sum_{n=1}^{\\infty}\\frac{(-1)^n}{\\sqrt{n}}"));
        assertEquals("= ∞", Calc.once("\\sum_{n=1}^{\\infty}\\frac{1}{\\sqrt{n}}"));
        assertEquals("true", Calc.once("\\sum_{n=1}^{\\infty}\\frac{1}{n^2}=\\frac{\\pi^2}{6}"));
        assertEquals("= 0", Calc.once("\\sum_{n=-3}^{3}n^3"));
        assertEquals("= 1", Calc.once("\\prod_{n=5}^{1}n"));
    }

    @Test
    void hardEquations() {
        assertEquals("no solution", Calc.once("\\frac{x^2-1}{x-1}=2"));
        assertEquals("x = 6", Calc.once("\\sqrt{x+3}=x-3"));
        assertEquals("-1 ≤ x ≤ 1", Calc.once("|x-1|+|x+1|=2"));
        assertEquals("x = -0.77 or x = 2 or x = 4", Calc.once("2^{x}=x^{2}"));
        assertEquals("x = 1.56", Calc.once("x^x=2"));
        assertEquals("x = -8.42 or x = -7.07 or x = -2.85 or x = 0 or x = 2.85"
                + " or x = 7.07 or x = 8.42", Calc.once("\\sin x=\\frac{x}{10}"));
        assertEquals("x = 4.49", Calc.once("\\tan x=x\\land4<x\\land x<5"));
        assertEquals("x = 4", Calc.once("\\log_{2}(x)+\\log_{2}(x-2)=3"));
        assertEquals("x = -3 or x = 3", Calc.once("x^2=4\\pm5"));
        assertEquals("? = -4 or ? = 4", Calc.once("?^2=16"));
        assertEquals("α = -1 or α = 3", Calc.once("\\alpha^2-2\\alpha=3"));
        assertEquals("x = -1000 or x = 1000", Calc.once("x^2=10^{6}"));
        assertEquals("no solution", Calc.once("\\frac{\\sin x}{x}=1"));
        assertEquals("x = -8", Calc.once("\\sqrt[3]{x}=-2"));
        assertEquals("x = -0.96 or x = 4", Calc.once("x!=24"));
        assertEquals("x = 20", Calc.once("50\\%x=10"));
        assertEquals("x = -2/3 or x = 1", Calc.once("\\frac{1}{x}+\\frac{1}{x+1}=\\frac{3}{2}"));
    }

    @Test
    void hardInequalities() {
        assertEquals("x < -1 or 0 < x < 1", Calc.once("\\frac{1}{x}>x"));
        assertEquals("-2 ≤ x ≤ -1 or 1 ≤ x ≤ 2", Calc.once("x^4-5x^2+4\\leq0"));
        assertEquals("x > 13.82", Calc.once("e^{x}>10^{6}"));
        assertEquals("x > 1", Calc.once("0<\\frac{1}{x}<1"));
        assertEquals("0 ≤ x < 4", Calc.once("\\sqrt{x}<2"));
        assertEquals("-1 ≤ x ≤ 0 or x ≥ 1", Calc.once("x^3\\geq x"));
        assertEquals("-2 < x < 2 or x > 2", Calc.once("\\frac{x^2-4}{x-2}>0"));
        assertEquals("1 < x < 2 or 2 < x < 3", Calc.once("1<x<3\\land x\\neq2"));
        assertEquals("x < 0 or x ≥ 1", Calc.once("\\frac{x-1}{x}\\geq0"));
    }

    @Test
    void hardQuantifiers() {
        assertEquals("true", Calc.once("\\exists x\\in\\mathbb{R}, \\forall y\\in\\mathbb{R}, x\\leq y^2"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, \\exists y\\in\\mathbb{R}, y^3=x"));
        assertEquals("true", Calc.once("\\forall\\epsilon\\in\\mathbb{R}, \\epsilon>0\\Rightarrow\\exists\\delta"
                + "\\in\\mathbb{R}, \\delta>0\\land\\delta<\\epsilon"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, e^{x}\\geq1+x"));
        assertEquals("false", Calc.once("\\forall x\\in\\mathbb{R}, e^{x}>1+x"));
        assertEquals("false", Calc.once("\\forall x\\in\\mathbb{R}, \\frac{x^2}{x}=x"));
        assertEquals("true", Calc.once("\\exists x\\in\\mathbb{N}, \\forall y\\in\\mathbb{N}, x\\leq y"));
        assertEquals("false", Calc.once("\\exists x\\in\\mathbb{Z}, \\forall y\\in\\mathbb{Z}, x\\leq y"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, x\\neq0\\Rightarrow\\frac{\\sin x}{x}<1"));
        assertEquals("true", Calc.once("\\forall x\\in\\{\\}, x\\neq x"));
        assertEquals("true", Calc.once("\\forall n\\in\\{1,2,3,4,5\\}, \\prod_{k=1}^{n}k=n!"));
        assertEquals("true", Calc.once("\\exists x\\in\\mathbb{R}, \\lim_{t\\rightarrow x}\\frac{t^2-x^2}{t-x}=4"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, (x,1)\\cdot(1,x)=2x"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, x^4-4x^3+6x^2-4x+1\\geq0"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, x^{2}+10^{-9}>0"));
        assertEquals("false", Calc.once("\\exists n\\in\\mathbb{N}, n!>10^{6}\\land n<10"));
    }

    @Test
    void functionsInsideEverything() {
        Calc calc = new Calc();
        calc.show("f(x)=\\frac{x^2-1}{x+1}");
        calc.show("g(x,y)=\\sqrt{x^2+y^2}");
        assertEquals("= -2", calc.show("\\lim_{x\\rightarrow-1}f(x)"));
        assertEquals("false", calc.show("\\exists x\\in\\mathbb{R}, f(x)=-2"));
        assertEquals("= 18 or -8", calc.show("g(3,4)\\pm g(5,12)"));
        assertEquals("true", calc.show("|(3,4)|=g(3,4)"));
        assertEquals("= 0.6", calc.show("\\frac{\\partial g}{\\partial x}(3,4)"));
        assertEquals("= {5}", calc.show("\\{g(3,4),g(4,3),5\\}"));
        assertEquals("x = -7 or x = 7", calc.show("g(x,0)=7"));
    }

    // --- bugs found while testing, now fixed ---

    @Test
    void poleIsNeverAnIncludedEndpoint() {
        assertEquals("x < -2 or x ≥ 1", Calc.once("\\frac{x-1}{x+2}\\geq0"));
        assertEquals("x < -2 or x > 1", Calc.once("\\frac{x-1}{x+2}>0"));
        assertEquals("x > -2", Calc.once("\\frac{1}{x+2}\\geq0"));
        assertEquals("x ≥ 1", Calc.once("\\sqrt{x-1}\\geq0"));
    }

    @Test
    void rootsPastRangeKeptWithRootsInRange() {
        assertEquals("x = 99 or x = 101", Calc.once("x^{2}-200x+9999=0"));
        assertEquals("x = 50 or x = 150", Calc.once("(x-50)(x-150)=0"));
        assertEquals("x = -500 or x = 3", Calc.once("(x+500)(x-3)=0"));
    }

    @Test
    void tinyConstantsCompareExactly() {
        assertEquals("true", Calc.once("10^{-10}>0"));
        assertEquals("true", Calc.once("10^{-20}\\neq0"));
        assertEquals("false", Calc.once("10^{-10}=0"));
        assertEquals("false", Calc.once("1+10^{-10}=1"));
        assertEquals("false", Calc.once("0.333333333=\\frac{1}{3}"));
        // float noise still forgiven
        assertEquals("true", Calc.once("0.1+0.2=0.3"));
        assertEquals("true", Calc.once("(0.1+0.2-0.3)\\cdot1000=0"));
        assertEquals("true", Calc.once("\\sin(\\pi)=0"));
        assertEquals("true", Calc.once("e^{\\ln 2}=2"));
        assertEquals("true", Calc.once("\\sqrt{2}^2=2"));
        assertEquals("true", Calc.once("\\tan(\\frac{\\pi}{4})=1"));
    }

    @Test
    void boundVariableShadowsFunction() {
        Calc calc = new Calc();
        calc.show("h(x)=x^2");
        assertEquals("= 0.54", calc.show("\\lim_{h\\rightarrow0}\\frac{\\sin(1+h)-\\sin(1)}{h}"));
        assertEquals("= 6", calc.show("\\sum_{h=1}^{3}h"));
        assertEquals("true", calc.show("\\forall h\\in\\{1,2\\}, h>0"));
        assertEquals("= 9", calc.show("h(3)"));
    }

    @Test
    void implicitMultiplyBeforeAbsolute() {
        assertEquals("= 6", Calc.once("3|-2|"));
        assertEquals("x = -2 or x = 2", Calc.once("5|x|=10"));
        assertEquals("x = -3 or x = -2 or x = 2 or x = 3", Calc.once("x^2-5|x|+6=0"));
        assertEquals("x = -2 or x = 0 or x = 2", Calc.once("||x|-1|=1"));
        assertEquals("x = -2 or x = 2", Calc.once("|x||x|=4"));
    }

    @Test
    void periodicBoundsAlwaysTrue() {
        assertEquals("true for all x", Calc.once("\\sin x\\leq1"));
        assertEquals("true for all x", Calc.once("-1\\leq\\sin x\\leq1"));
        // these really fail at 2πk
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\cos x<1")));
    }

    @Test
    void oddRootOfNegativeViaPower() {
        assertEquals("= -2", Calc.once("(-8)^{\\frac{1}{3}}"));
        assertEquals("= 4", Calc.once("(-8)^{\\frac{2}{3}}"));
        assertEquals("= undefined", Calc.once("(-8)^{0.5}"));
        assertEquals("x = -8", Calc.once("x^{\\frac{1}{3}}=-2"));
    }

    @Test
    void sumBodyStopsAtPlusAndMinus() {
        assertEquals("= 7", Calc.once("\\sum_{n=1}^{3}n+1"));
        assertEquals("= 9", Calc.once("\\sum_{n=1}^{3}(n+1)"));
        assertEquals("= 7", Calc.once("\\prod_{n=1}^{3}n+1"));
        assertEquals("= 12", Calc.once("\\sum_{n=1}^{3}2n"));
        assertEquals("= -6", Calc.once("\\sum_{n=1}^{3}-n"));
        assertEquals("= 0.64", Calc.once("\\sum_{n=1}^{\\infty}\\frac{1}{n^2}-1"));
        assertEquals("= 19", Calc.once("\\sum_{i=1}^{3}\\sum_{j=1}^{2}ij+1"));
        assertEquals("= 47/6", Calc.once("\\sum_{n=1}^{3}\\frac{1}{n}+\\sum_{n=1}^{3}n"));
        // 1.002 would look like a plain 1, so it keeps digits
        assertEquals("= 1.0021", Calc.once("\\sum_{n=1}^{\\infty}\\frac{1}{n^3}-\\prod_{n=1}^{4}\\frac{n}{n+1}"));
    }

    @Test
    void intersectionBindsTighterThanUnion() {
        assertEquals("= {1, 2, 3, 4}", Calc.once("\\{1,2,3\\}\\cup\\{3,4\\}\\cap\\{4,5\\}"));
        assertEquals("= {4}", Calc.once("(\\{1,2,3\\}\\cup\\{3,4\\})\\cap\\{4,5\\}"));
        assertEquals("= {2, 9}", Calc.once("\\{1,2\\}\\cap\\{2,3\\}\\cup\\{9\\}"));
        assertEquals("false", Calc.once("5\\in\\{1\\}\\cup\\{5\\}\\cap\\{6\\}"));
        assertEquals("false", Calc.once("\\exists x\\in\\{1,2\\}\\cup\\{3,4\\}\\cap\\{4\\}, x=3"));
    }

    @Test
    void rootsPastWhatDoublesHoldAreNotNoSolution() {
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\ln x=1000")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\ln x>1000")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("x^{0.001}=2")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\sqrt{x}=10^{200}")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("(\\ln x-1000)(x-1)=0")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\exists x\\in\\mathbb{R}, \\ln x=1000")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\exists x\\in\\mathbb{R}, \\ln(\\ln x)=10")));
        // asymptotes still have no root
        assertEquals("no solution", Calc.once("\\frac{1}{x}=0"));
        assertEquals("no solution", Calc.once("e^{-x}=0"));
        assertEquals("false", Calc.once("\\exists x\\in\\mathbb{R}, \\frac{1}{x}=0"));
        assertEquals("x = -2", Calc.once("\\frac{x}{x+1}=2"));
    }

    @Test
    void fewRootsInRangeButEndlessPastIt() {
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\sin(x/100)=0")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\cos(\\frac{x}{50})=1")));
        assertEquals("x = 50 or x = 150", Calc.once("(x-50)(x-150)=0"));
    }

    @Test
    void roundingNeverFakesAnExactAnswer() {
        assertEquals("x ≠ 1.001", Calc.once("x\\neq1.001"));
        assertEquals("x > 1.001", Calc.once("x>1.001"));
        assertEquals("x = -1.001 or x = 1.001", Calc.once("x^2=1.002001"));
        assertEquals("= 3.14", Calc.once("\\pi"));
        assertTrue(Calc.once("\\sin x=0").contains("x = -75.4 or"));
    }

    @Test
    void tinyValuesWithAVariable() {
        assertEquals("-0.00001 < x < 0.00001", Calc.once("x^2<10^{-10}"));
        assertEquals("-0.00001 ≤ x ≤ 0.00001", Calc.once("x^2\\leq10^{-10}"));
        assertEquals("x = -0.0000001 or x = 0.0000001", Calc.once("x^2=10^{-14}"));
        assertEquals("-0.000000000001 < x < 0.000000000001", Calc.once("|x|<10^{-12}"));
        assertEquals("true", Calc.once("\\exists x\\in\\mathbb{R}, 0<x\\land x<10^{-10}"));
        assertEquals("false", Calc.once("\\forall x\\in\\mathbb{R}, x^2\\geq10^{-12}"));
        // noisy spots keep the usual band
        assertEquals("x < 1 or x > 1", Calc.once("\\frac{x^2-1}{x-1}\\neq2"));
        assertEquals("true for all x", Calc.once("e^{x}+1>e^{x}"));
    }

    @Test
    void differenceQuotientAtLargeX() {
        assertEquals("= 2000000", Calc.once("\\lim_{h\\rightarrow0}\\frac{(1000000+h)^2-1000000^2}{h}"));
        assertEquals("= -2000000", Calc.once("\\lim_{h\\rightarrow0}\\frac{(-1000000+h)^2-1000000^2}{h}"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, \\lim_{h\\rightarrow0}\\frac{(x+h)^2-x^2}{h}=2x"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, \\lim_{h\\rightarrow0}\\frac{\\sin(x+h)-\\sin(x)}{h}"
                + "=\\cos x"));
        // too noisy to trust -> not a made up number
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\lim_{h\\rightarrow0}\\frac{(10^8+h)^2-10^{16}}{h}")));
        // real wobbles still have no limit
        assertEquals("= undefined", Calc.once("\\lim_{x\\rightarrow\\infty}x\\sin x"));
        assertEquals("= undefined", Calc.once("\\lim_{x\\rightarrow0}\\frac{1}{x}\\sin(\\frac{1}{x})"));
        assertEquals("= ∞", Calc.once("\\lim_{x\\rightarrow0}e^{\\frac{1}{x^2}}"));
    }

    @Test
    void factorialOfNonWholeNumbers() {
        assertEquals("= 3.32", Calc.once("(2.5)!"));
        assertEquals("= 1.77", Calc.once("(-0.5)!"));
        assertEquals("= undefined", Calc.once("(-1.5)!"));
        assertEquals("= undefined", Calc.once("(-3)!"));
        assertEquals("x = -0.96 or x = 4", Calc.once("x!=24"));
        assertEquals("= -0.58", Calc.once("\\lim_{x\\rightarrow0}\\frac{x!-1}{x}"));
        // creeping up when x! overflows isnt evidence of inf
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\lim_{x\\rightarrow\\infty}\\frac{\\ln(x!)}{x\\ln x}")));
        assertEquals("= ∞", Calc.once("\\lim_{x\\rightarrow\\infty}e^{x}"));
    }

    @Test
    void tooComplexFormulasSaySo() {
        String deep = "(".repeat(50000) + "1" + ")".repeat(50000);
        assertEquals("Uncalculable — Formula too complex", Calc.once(deep));
        assertEquals("Uncalculable — Formula too complex",
                Calc.once("\\sum_{i=1}^{1000}\\sum_{j=1}^{1000}\\sum_{k=1}^{100}ijk"));
        assertEquals("Uncalculable — Formula too complex",
                Calc.once("\\forall n\\in\\mathbb{N}, \\sum_{i=1}^{n}i=\\frac{n(n+1)}{2}"));
    }
}
