package entity;

import entity.node.Node;
import entity.node.NumberNode;
import entity.value.BooleanValue;
import entity.value.FiniteSetValue;
import entity.value.NumberValue;
import entity.value.Value;
import entity.value.Values;
import entity.value.VectorValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MathParserTest {
    private static final double DELTA = 1e-9;

    private Value eval(String text) {
        return new MathParser(MathLexer.tokenize(text), new HashMap<>()).parse().evaluate(new Context(new HashMap<>()));
    }

    private double num(String text) {
        return Values.toNumber(eval(text));
    }

    private double numWithX(String text, double x) {
        Context context = new Context(new HashMap<>()).withVariable("x", x);
        return Values.toNumber(new MathParser(MathLexer.tokenize(text), new HashMap<>()).parse().evaluate(context));
    }

    @Test
    void followsOperatorPriority() {
        assertEquals(7, num("1+2*3"), DELTA);
        assertEquals(9, num("(1+2)*3"), DELTA);
        assertEquals(2.5, num("10/4"), DELTA);
        assertEquals(1, num("8-4-3"), DELTA);
        assertEquals(1, num("12/4/3"), DELTA);
    }

    @Test
    void powers() {
        assertEquals(512, num("2^3^2"), DELTA);
        assertEquals(-4, num("-2^2"), DELTA);
        assertEquals(0.5, num("2^{-1}"), DELTA);
        assertEquals(9, numWithX("x^{2}", 3), DELTA);
    }

    @Test
    void latexOperators() {
        assertEquals(12, num("4\\times3"), DELTA);
        assertEquals(12, num("4\\cdot3"), DELTA);
        assertEquals(12, num("4·3"), DELTA);
        assertEquals(1, num("5\\div5"), DELTA);
        assertEquals(1.2, num("\\frac{6}{5}"), DELTA);
        assertEquals(9, num("\\left(1+2\\right)\\times3"), DELTA);
    }

    @Test
    void hiddenMultiplication() {
        assertEquals(6, numWithX("3x", 2), DELTA);
        assertEquals(14, num("2(3+4)"), DELTA);
        assertEquals(21, num("(1+2)(3+4)"), DELTA);
        assertEquals(2 * Math.PI, num("2\\pi"), DELTA);
        assertEquals(-6, numWithX("-3x", 2), DELTA);
    }

    @Test
    void roots() {
        assertEquals(4, num("\\sqrt{16}"), DELTA);
        assertEquals(-2, num("\\sqrt[3]{-8}"), DELTA);
        assertEquals(3, num("sqrt(9)"), DELTA);
    }

    @Test
    void functionsAndConstants() {
        assertEquals(0, num("\\sin(0)"), DELTA);
        assertEquals(-1, num("\\cos(\\pi)"), DELTA);
        assertEquals(1, num("\\sin(\\pi/2)^2"), DELTA);
        assertEquals(1, num("\\ln(e)"), DELTA);
        assertEquals(3, num("\\log(1000)"), DELTA);
        assertEquals(3, num("\\log_2 8"), DELTA);
        assertEquals(3, num("\\log_{2}{8}"), DELTA);
    }

    @Test
    void factorialAndAbsoluteValue() {
        assertEquals(6, num("3!"), DELTA);
        assertEquals(1, num("0!"), DELTA);
        assertEquals(5, num("|-5|"), DELTA);
        assertEquals(3, num("|1-4|"), DELTA);
    }

    @Test
    void undefinedResultsAreNotNumbers() {
        assertTrue(Double.isNaN(num("\\sqrt{-1}")));
        // non-whole n! = gamma(n+1)
        assertEquals(3.3233509704478426, num("2.5!"), 1e-12);
        assertTrue(Double.isNaN(num("(-1.5)!")));
    }

    @Test
    void reportsSyntaxErrors() {
        assertThrows(MathSyntaxException.class, () -> num("1+"));
        assertThrows(MathSyntaxException.class, () -> num("(1+2"));
        assertThrows(MathSyntaxException.class, () -> num("1+2)"));
        assertThrows(MathSyntaxException.class, () -> num("\\text{hi}"));
        assertThrows(MathSyntaxException.class, () -> num("1.2.3"));
        assertThrows(MathSyntaxException.class, () -> num("x+1"));
        assertThrows(MathSyntaxException.class, () -> num(""));
    }

    @Test
    void keepsExistingErrorMessages() {
        assertEquals("Unexpected \")\".", assertThrows(MathSyntaxException.class, () -> num("1+2)")).getMessage());
        assertEquals("The expression ends too early.",
                assertThrows(MathSyntaxException.class, () -> num("1+")).getMessage());
        assertEquals("Expected \")\".", assertThrows(MathSyntaxException.class, () -> num("(1+2")).getMessage());
        assertEquals("The expression is empty.", assertThrows(MathSyntaxException.class, () -> num("")).getMessage());
        assertEquals("Unknown variable \"x\".", assertThrows(MathSyntaxException.class, () -> num("x+1")).getMessage());
        assertEquals("\"1.2.3\" is not a valid number.",
                assertThrows(MathSyntaxException.class, () -> num("1.2.3")).getMessage());
        assertEquals("\"\\foo\" is not supported.",
                assertThrows(MathSyntaxException.class, () -> num("\\foo")).getMessage());
    }

    @Test
    void divisionByZeroIsUndefinedNotInfinite() {
        assertTrue(Double.isNaN(num("1/0")));
        assertTrue(Double.isNaN(num("0/0")));
    }

    @Test
    void logOfZeroAndTanPoleAreUndefined() {
        assertTrue(Double.isNaN(num("\\ln 0")));
        assertTrue(Double.isNaN(num("\\log 0")));
        assertTrue(Double.isNaN(num("\\tan(\\pi/2)")));
    }

    @Test
    void zeroToNegativePowerIsUndefined() {
        assertTrue(Double.isNaN(num("0^{-1}")));
    }

    @Test
    void equalityIsARelation() {
        assertTrue(Values.toBoolean(eval("5+7=12")));
        assertFalse(Values.toBoolean(eval("5+7=13")));
    }

    @Test
    void freeVariablesAreCollectedInOrder() {
        Set<String> names = new LinkedHashSet<>();
        new MathParser(MathLexer.tokenize("y+2x=e"), new HashMap<>()).parse().collectFreeVariables(names);
        assertEquals(Arrays.asList("y", "x"), new ArrayList<>(names));
    }

    @Test
    void canBeInfiniteIsFalseForPlainArithmetic() {
        assertFalse(new MathParser(MathLexer.tokenize("10^{400}"), new HashMap<>()).parse().canBeInfinite());
    }

    @Test
    void contextScopesVariablesAndCountsSteps() {
        Context outer = new Context(new HashMap<>()).withVariable("x", 1);
        Context inner = outer.withVariable("x", 2).withVariable("y", 3);
        assertEquals(1, outer.getVariable("x"), DELTA);
        assertEquals(2, inner.getVariable("x"), DELTA);
        assertEquals(2, inner.getVariableValues().size());
        assertFalse(inner.forFunctionBody().hasVariable("x"));
        assertTrue(inner.withSigns(2).isPlus(0));
        assertFalse(inner.withSigns(2).isPlus(1));
        Context budget = new Context(new HashMap<>());
        for (int i = 0; i < Context.MAX_STEPS; i++) {
            budget.spendStep();
        }
        assertThrows(BudgetExceededException.class, () -> budget.withVariable("x", 1).spendStep());
    }

    @Test
    void functionBodiesNestOnlyFiftyDeep() {
        Context context = new Context(new HashMap<>());
        for (int i = 0; i < 50; i++) {
            context = context.forFunctionBody();
        }
        Context deepest = context;
        assertThrows(MathSyntaxException.class, deepest::forFunctionBody);
    }

    @Test
    void valuesCompareByContent() {
        FiniteSetValue set = new FiniteSetValue(Arrays.asList(3.0, 1.0, 1.0 + 1e-12, 2.0));
        assertEquals(Arrays.asList(1.0, 2.0, 3.0), set.getElements());
        assertTrue(Values.areEqual(set, new FiniteSetValue(Arrays.asList(2.0, 3.0, 1.0))));
        assertTrue(Values.areEqual(new VectorValue(Arrays.asList(1.0, 2.0)), new VectorValue(Arrays.asList(1.0, 2.0))));
        assertFalse(Values.areEqual(new NumberValue(1), BooleanValue.of(true)));
        assertTrue(Values.areAlmostEqual(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY));
        assertFalse(Values.areAlmostEqual(Double.POSITIVE_INFINITY, 1e308));
        assertFalse(Values.areAlmostEqual(Double.NaN, Double.NaN));
        assertThrows(UncalculableException.class, () -> Values.toNumber(BooleanValue.of(true)));
    }

    private Node parse(String text, Map<String, UserFunction> functions) {
        return new MathParser(MathLexer.tokenize(text), functions).parse();
    }

    private UserFunction function(String name, String parameter, String body, Map<String, UserFunction> functions) {
        return new UserFunction(name, Collections.singletonList(parameter), parse(body, functions), body);
    }

    @Test
    void userFunctions() {
        Map<String, UserFunction> functions = new HashMap<>();
        functions.put("f", function("f", "x", "x^2+1", functions));
        double result = Values.toNumber(parse("f(3)+f(0)", functions).evaluate(new Context(functions)));
        assertEquals(11, result, DELTA);
        assertEquals("Write f(...) to use the function f.",
                assertThrows(MathSyntaxException.class, () -> parse("f+1", functions)).getMessage());
    }

    @Test
    void functionThatCallsItselfStops() {
        Map<String, UserFunction> functions = new HashMap<>();
        functions.put("f", new UserFunction("f", Collections.singletonList("x"), new NumberNode(0, 0), "0"));
        functions.put("f", function("f", "x", "f(x)+1", functions));
        Node call = parse("f(1)", functions);
        MathSyntaxException error = assertThrows(MathSyntaxException.class,
                () -> call.evaluate(new Context(functions)));
        assertEquals("The function f keeps calling itself, so it cannot be calculated.", error.getMessage());
    }
}
