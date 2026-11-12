package use_case.evaluate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionAndDerivativeTest {
    @Test
    void functionsAndDerivativesInOneSession() {
        Calc c = new Calc();
        assertEquals("Defined T(x,y) = 3x+4y", c.show("T(x,y)=3x+4y"));
        assertEquals("= 11", c.show("T(1,2)"));
        assertEquals("Invalid formula — T needs 2 inputs", c.show("T(1)"));
        assertEquals("Defined f(x) = x^3", c.show("f(x)=x^3"));
        assertEquals("= 12", c.show("f'(2)"));
        assertEquals("= 12", c.show("f''(2)"));
        assertEquals("= 6", c.show("f'''(2)"));
        assertEquals("= 0", c.show("f^{(4)}(2)"));
        assertEquals("Uncalculable", Calc.withoutReason(c.show("f^{(5)}(2)")));
        assertEquals("x = 3", c.show("\\frac{d}{dx}x^2=6"));
        assertEquals("x = 2", c.show("\\frac{d^2}{dx^2}x^3=12"));
        assertEquals("x = 1", c.show("\\frac{\\partial}{\\partial x}(x^2+3x)=5"));
        c.show("g(x,y)=x^2y");
        assertEquals("= 12", c.show("\\frac{\\partial g}{\\partial x}(3,2)"));
        c.show("h(x)=|x|");
        assertEquals("= undefined", c.show("h'(0)"));
    }

    @Test
    void xIsNeverAFunctionName() {
        Calc c = new Calc();
        assertFalse(c.show("x(y)=3").startsWith("Defined"), c.show("x(y)=3"));
        assertEquals("x = -2 or x = 2", c.show("x^2=4"));
        assertEquals("Defined y(x) = x^2", c.show("y(x)=x^2"));
    }

    @Test
    void redefinedFunctionIsSeenByItsCallers() {
        Calc c = new Calc();
        c.show("m(x)=x");
        c.show("n(x)=m(x)+1");
        assertEquals("= 2", c.show("n(1)="));
        c.show("m(x)=\\infty");
        assertEquals("= ∞", c.show("n(1)="));
        c.show("m(x)=10^{400}x");
        assertEquals("Uncalculable — Number too big", c.show("n(1)="));
    }

    @Test
    void derivativeSyntaxOutsideFunctions() {
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("f'(x)=\\cos(e^{x})")));
        assertEquals("= 2x", Calc.once("\\frac{d}{dx}x^2"));
        assertEquals("d = 2", Calc.once("d+1=3"));
    }

    @Test
    void multiParameterDefinitionMessages() {
        Calc c = new Calc();
        assertEquals("Invalid formula — T(x,y) can only use x, y, not z", c.show("T(x,y)=x+y+z"));
        assertEquals("Invalid formula — f(x) can only use x, not y", c.show("f(x)=x+y"));
        assertEquals("Defined r(x) = \\frac{1}{x-1}", c.show("r(x)=\\frac{1}{x-1}"));
        assertEquals("Defined q(x) = 2x", c.show("q(x)=2x"));
        assertEquals("= 2", c.show("\\frac{dq}{dx}(5)"));
        assertEquals("true for all x", c.show("\\frac{d}{dx}3x+4=3"));
        assertEquals("= 6", Calc.once("\\frac{4}{2}3"));
    }

    @Test
    void derivativeEquationsKeepEveryRoot() {
        Calc c = new Calc();
        c.show("f(x)=\\sin(x)");
        String roots = c.show("f'(x)=0");
        assertTrue(roots.startsWith("x = -98.96 or x = -95.82 or "), roots);
        assertTrue(roots.endsWith("(and 54 more)"), roots);
        assertTrue(c.show("\\frac{d}{dx}\\sin(x)=0").startsWith("x = -98.96 or "));
        assertEquals("x = 0.69", c.show("\\frac{d}{dx}e^{x}=2"));
        c.show("v(x)=\\frac{1}{x}");
        assertEquals("= -400", c.show("v'(0.05)"));
    }

    @Test
    void approximateResultsAreRoundedForDisplay() {
        Calc c = new Calc();
        c.show("f(x)=x^3");
        assertEquals("= 12", c.show("f'(2)"));
        assertEquals("= 13", c.show("f'(2)+1"));
        assertEquals("= (12, 1)", c.show("(f'(2),1)"));
        c.show("k(x)=f'(x)");
        assertEquals("= 12", c.show("k(2)"));
        c.show("p(x)=e^{x}");
        assertEquals("= 2.72", c.show("p'(1)"));
    }

    @Test
    void largeFunctionValuesDoNotHideDerivatives() {
        Calc c = new Calc();
        c.show("g(x)=x^2+1000000");
        assertEquals("= 0.6", c.show("g'(0.3)"));
        c.show("m(x)=x^3+10^{9}");
        // m only good to ~1e-7 so roots are too
        String[] roots = c.show("m'(x)=0.03").split(" or ");
        assertEquals(2, roots.length);
        assertEquals(-0.1, Double.parseDouble(roots[0].substring(4)), 1e-6);
        assertEquals(0.1, Double.parseDouble(roots[1].substring(4)), 1e-6);
        c.show("s(x)=1000000\\sin(x)");
        assertEquals("= 0", c.show("s'(\\frac{\\pi}{2})"));
        String sRoots = c.show("s'(x)=0");
        assertTrue(sRoots.startsWith("x = -98.96 or x = -95.82 or "), sRoots);
        assertTrue(sRoots.endsWith("(and 54 more)"), sRoots);
        c.show("u(x)=10^{8}\\sin(x)");
        String uRoots = c.show("u'(x)=0");
        assertTrue(uRoots.startsWith("x = -98.96 or x = -95.82 or "), uRoots);
        assertTrue(uRoots.endsWith("(and 54 more)"), uRoots);
        c.show("h(x)=|x|");
        assertEquals("= undefined", c.show("h'(0)"));
    }

    @Test
    void knownFormulaGivesExactDerivatives() {
        Calc c = new Calc();
        c.show("f(x)=x^{x^x}");
        assertEquals("= 766906545381.25", c.show("f'(2.9)"));
        c.show("f(x)=e^{e^x}");
        assertEquals("= 1420316213.66", c.show("f'(2.9)"));
        c.show("k(x)=\\frac{1}{x}");
        assertEquals("= 240000000000", c.show("k''''(0.01)"));
        c.show("h(x)=x^{20}");
        assertEquals("= 1.774291992×10^32", c.show("h''''(50)"));
        c.show("q(x)=e^x");
        assertEquals("= 5.184705529×10^21", c.show("q'(50)"));
        c.show("f(x)=e^{x^2}");
        assertEquals("= 14099366.03", c.show("f''''(3)"));
        c.show("g(x)=\\sin(100x)");
        assertEquals("= -50636564.11", c.show("g''''(1)"));
    }

    @Test
    void exactDerivativeOnlyWhereTheFunctionIsDefined() {
        Calc c = new Calc();
        c.show("u(x)=x^{1/3}");
        assertEquals("= undefined", c.show("u'(0)"));
        assertEquals("= 1/12", c.show("u'(8)"));
        assertEquals("= 1/12", c.show("u'(-8)"));
        c.show("r(x)=x^{3/2}");
        assertEquals("= undefined", c.show("r'(0)"));
        assertEquals("= 3", c.show("r'(4)"));
        c.show("m(x)=\\ln x");
        assertEquals("= undefined", c.show("m'(-1)"));
        c.show("s(x)=\\sin x");
        assertEquals("= 0", c.show("s''(\\pi)"));
    }

    @Test
    void derivativeEquationsUseTheFormula() {
        assertEquals("true for all x", Calc.once("\\frac{d^4}{dx^4}\\sin x=\\sin x"));
        assertEquals("x = 1", Calc.once("\\frac{d}{dx}\\ln x=1"));
        assertEquals("x = 1", Calc.once("\\frac{d^2}{dx^2}\\ln x=-1"));
    }

    @Test
    void exactDerivativeSeesARedefinedCallee() {
        Calc c = new Calc();
        c.show("g(x)=x^2");
        c.show("f(x)=g(x)+1");
        assertEquals("= 6", c.show("f'(3)"));
        c.show("g(x)=x^3");
        assertEquals("= 27", c.show("f'(3)"));
        c.show("p(x)=f'(x)");
        assertEquals("= 27", c.show("p(3)"));
        c.show("g(x)=x^4");
        assertEquals("= 108", c.show("p(3)"));
        assertEquals("= 108", c.show("\\frac{df}{dx}(3)"));
    }

    @Test
    void derivativeVariableStaysFree() {
        assertEquals("true for all x", Calc.once("\\frac{d}{dx}5=0"));
        assertEquals("= 2x", Calc.once("\\frac{d}{dx}x^2"));
        assertEquals("x = 2", Calc.once("\\frac{d^{2}}{dx^{2}}x^3=12"));
    }
}
