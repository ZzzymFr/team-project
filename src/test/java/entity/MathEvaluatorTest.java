package entity;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MathEvaluatorTest {
    private static final double DELTA = 1e-9;

    private double calculate(String text) {
        return new MathEvaluator(MathLexer.tokenize(text), new HashMap<>(), new HashMap<>()).evaluate();
    }

    private double calculateWithX(String text, double x) {
        Map<String, Double> variables = new HashMap<>();
        variables.put("x", x);
        return new MathEvaluator(MathLexer.tokenize(text), variables, new HashMap<>()).evaluate();
    }

    @Test
    void followsOperatorPriority() {
        assertEquals(7, calculate("1+2*3"), DELTA);
        assertEquals(9, calculate("(1+2)*3"), DELTA);
        assertEquals(2.5, calculate("10/4"), DELTA);
        assertEquals(1, calculate("8-4-3"), DELTA);
        assertEquals(1, calculate("12/4/3"), DELTA);
    }

    @Test
    void powers() {
        assertEquals(512, calculate("2^3^2"), DELTA);
        assertEquals(-4, calculate("-2^2"), DELTA);
        assertEquals(0.5, calculate("2^{-1}"), DELTA);
        assertEquals(9, calculateWithX("x^{2}", 3), DELTA);
    }

    @Test
    void latexOperators() {
        assertEquals(12, calculate("4\\times3"), DELTA);
        assertEquals(1, calculate("5\\div5"), DELTA);
        assertEquals(1.2, calculate("\\frac{6}{5}"), DELTA);
        assertEquals(9, calculate("\\left(1+2\\right)\\times3"), DELTA);
    }

    @Test
    void hiddenMultiplication() {
        assertEquals(6, calculateWithX("3x", 2), DELTA);
        assertEquals(14, calculate("2(3+4)"), DELTA);
        assertEquals(21, calculate("(1+2)(3+4)"), DELTA);
        assertEquals(2 * Math.PI, calculate("2\\pi"), DELTA);
        assertEquals(-6, calculateWithX("-3x", 2), DELTA);
    }

    @Test
    void roots() {
        assertEquals(4, calculate("\\sqrt{16}"), DELTA);
        assertEquals(-2, calculate("\\sqrt[3]{-8}"), DELTA);
        assertEquals(3, calculate("sqrt(9)"), DELTA);
    }

    @Test
    void functionsAndConstants() {
        assertEquals(0, calculate("\\sin(0)"), DELTA);
        assertEquals(-1, calculate("\\cos(\\pi)"), DELTA);
        assertEquals(1, calculate("\\sin(\\pi/2)^2"), DELTA);
        assertEquals(1, calculate("\\ln(e)"), DELTA);
        assertEquals(3, calculate("\\log(1000)"), DELTA);
        assertEquals(3, calculate("\\log_2 8"), DELTA);
        assertEquals(3, calculate("\\log_{2}{8}"), DELTA);
    }

    @Test
    void factorialAndAbsoluteValue() {
        assertEquals(6, calculate("3!"), DELTA);
        assertEquals(1, calculate("0!"), DELTA);
        assertEquals(5, calculate("|-5|"), DELTA);
        assertEquals(3, calculate("|1-4|"), DELTA);
    }

    @Test
    void undefinedResultsAreNotNumbers() {
        assertTrue(Double.isInfinite(calculate("1/0")));
        assertTrue(Double.isNaN(calculate("\\sqrt{-1}")));
        assertTrue(Double.isNaN(calculate("2.5!")));
    }

    @Test
    void userFunctions() {
        Map<String, UserFunction> functions = new HashMap<>();
        functions.put("f", new UserFunction("f", "x", MathLexer.tokenize("x^2+1"), "x^2+1"));
        double result = new MathEvaluator(MathLexer.tokenize("f(3)+f(0)"), new HashMap<>(), functions).evaluate();
        assertEquals(11, result, DELTA);
    }

    @Test
    void functionThatCallsItselfStops() {
        Map<String, UserFunction> functions = new HashMap<>();
        functions.put("f", new UserFunction("f", "x", MathLexer.tokenize("f(x)+1"), "f(x)+1"));
        MathEvaluator evaluator = new MathEvaluator(MathLexer.tokenize("f(1)"), new HashMap<>(), functions);
        assertThrows(MathSyntaxException.class, evaluator::evaluate);
    }

    @Test
    void reportsSyntaxErrors() {
        assertThrows(MathSyntaxException.class, () -> calculate("1+"));
        assertThrows(MathSyntaxException.class, () -> calculate("(1+2"));
        assertThrows(MathSyntaxException.class, () -> calculate("1+2)"));
        assertThrows(MathSyntaxException.class, () -> calculate("\\text{hi}"));
        assertThrows(MathSyntaxException.class, () -> calculate("1.2.3"));
        assertThrows(MathSyntaxException.class, () -> calculate("x+1"));
        assertThrows(MathSyntaxException.class, () -> calculate(""));
    }
}
