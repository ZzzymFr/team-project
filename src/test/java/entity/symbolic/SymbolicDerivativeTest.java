package entity.symbolic;

import entity.Context;
import entity.MathLexer;
import entity.MathParser;
import entity.MathSyntaxException;
import entity.UserFunction;
import entity.node.Node;
import entity.value.NumberValue;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymbolicDerivativeTest {
    private final Map<String, UserFunction> functions = new HashMap<>();

    private Node parse(String latex) {
        return new MathParser(MathLexer.tokenize(latex), functions).parse();
    }

    private String d(String latex) {
        String out = ExprWriter.latex(SymbolicDerivative.differentiate(parse(latex), functions));
        SimplifierTest.clean(out);
        return out;
    }

    private void define(String name, String parameter, String body) {
        functions.put(name, new UserFunction(name, List.of(parameter), parse(body), body));
    }

    private double at(String latex, double x) {
        String swapped = latex.replace("\\frac{dy}{dx}", "q");
        Context c = new Context(functions).withVariable("x", x).withVariable("y", 0.4).withVariable("q", 0.9);
        return ((NumberValue) parse(swapped).evaluate(c)).getValue();
    }

    // same function at a few points
    private void same(String expected, String actual) {
        for (double x : new double[] {0.7, 1.3, 2.1}) {
            assertEquals(at(expected, x), at(actual, x), 1e-9, actual + " vs " + expected + " at " + x);
        }
    }

    private void fails(String latex, String reason) {
        MathSyntaxException e = assertThrows(MathSyntaxException.class, () -> d(latex));
        assertTrue(e.getMessage().contains(reason), e.getMessage());
    }

    @Test
    void basicRules() {
        assertEquals("3x^{2}", d("\\frac{d}{dx}(x^3)"));
        assertEquals("0", d("\\frac{d}{dx}(7)"));
        assertEquals("10x", d("\\frac{d}{dx}(5x^2)"));
        assertEquals("2x+1", d("\\frac{d}{dx}(x^2+x)"));
        assertEquals("\\cos x", d("\\frac{d}{dx}(\\sin x)"));
        assertEquals("e^{x}", d("\\frac{d}{dx}(e^x)"));
        assertEquals("\\frac{1}{x}", d("\\frac{d}{dx}(\\ln x)"));
        assertEquals("\\frac{1}{2\\sqrt{x}}", d("\\frac{d}{dx}(\\sqrt{x})"));
        assertEquals("6(2x+1)^{2}", d("\\frac{d}{dx}((2x+1)^3)"));
        assertEquals("\\frac{1}{(x+1)^{2}}", d("\\frac{d}{dx}(\\frac{x}{x+1})"));
        assertEquals("6y", d("\\frac{d}{dy}(3y^{2})"));
    }

    @Test
    void equivalentAnswers() {
        same("\\sin x+x\\cos x", d("\\frac{d}{dx}(x\\sin x)"));
        same("x^{x}(\\ln x+1)", d("\\frac{d}{dx}(x^x)"));
        same("2x+2y\\frac{dy}{dx}", d("\\frac{d}{dx}(x^2+y^2)"));
        same("\\frac{1}{(\\cos x)^{2}}", d("\\frac{d}{dx}(\\tan x)"));
        same("-\\sin x", d("\\frac{d}{dx}\\cos x"));
        same("\\frac{1}{x\\ln 10}", d("\\frac{d}{dx}\\log x"));
        same("2^{x}\\ln 2", d("\\frac{d}{dx}2^x"));
        same("\\frac{1}{3}x^{-\\frac{2}{3}}", d("\\frac{d}{dx}\\sqrt[3]{x}"));
    }

    @Test
    void secondDerivative() {
        assertEquals("6x", d("\\frac{d^2}{dx^2}(x^3)"));
    }

    @Test
    void partialTreatsOtherLettersAsConstants() {
        assertEquals("y", d("\\frac{\\partial}{\\partial x}(xy)"));
    }

    @Test
    void definedFunctionPrimeIsEvaluatedAtItsArgument() {
        define("f", "y", "3y^{2}");
        assertEquals("6y", d("f'(y)"));
        assertEquals("12y", d("f'(2y)"));
        assertEquals("24y", d("\\frac{d}{dy}f(2y)"));
    }

    @Test
    void constantSubtreeIsEvaluated() {
        assertEquals("6", d("\\frac{d}{dx}(x\\sum_{n=1}^{3}n)"));
    }

    @Test
    void extraTermsStay() {
        assertEquals("2x+y", d("\\frac{d}{dx}(x^2)+y"));
        assertEquals("2x+1", d("\\frac{d}{dx}(x^2)+1"));
        assertEquals("2x+3", d("\\frac{d}{dx}(x^2+3x)"));
    }

    @Test
    void failures() {
        fails("\\frac{d}{dx}|x|", "Can't differentiate |x|");
        fails("\\frac{d}{dx}(x!)", "Can't differentiate n!");
        fails("\\frac{d^5}{dx^5}x", "Max 4th derivative");
    }

    @Test
    void decimalsStayDecimal() {
        assertEquals("5x", d("\\frac{d}{dx}(2.5x^2)"));
        assertEquals("0.5", d("\\frac{d}{dx}(0.5x)"));
    }

    @Test
    void containsDerivative() {
        define("f", "y", "3y^{2}");
        assertTrue(SymbolicDerivative.containsDerivative(parse("\\frac{d}{dx}x")));
        assertTrue(SymbolicDerivative.containsDerivative(parse("f'(x)")));
        assertTrue(SymbolicDerivative.containsDerivative(parse("1+\\frac{d}{dx}x")));
        assertFalse(SymbolicDerivative.containsDerivative(parse("x+1")));
        assertFalse(SymbolicDerivative.containsDerivative(parse("f(x)")));
    }

    @Test
    void quotientsEndUpAsOneFraction() {
        String root = d("\\frac{d}{dx}\\sqrt{\\frac{x}{x+1}}");
        assertEquals("\\frac{1}{2(x+1)^{2}\\sqrt{\\frac{x}{x+1}}}", root);
        assertEquals("\\frac{x^{2}-1}{x^{2}}", d("\\frac{d}{dx}\\frac{(x+1)^2}{x}"));
    }

    @Test
    void unrelatedFractionsStayApart() {
        assertEquals("-\\frac{1}{(x+1)^{2}}-\\frac{1}{x^{2}}", d("\\frac{d}{dx}(\\frac{1}{x}+\\frac{1}{x+1})"));
    }
}
