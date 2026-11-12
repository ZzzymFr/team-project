package entity.symbolic;

import entity.Context;
import entity.MathLexer;
import entity.MathParser;
import entity.UserFunction;
import entity.node.FallbackDerivative;
import entity.node.Node;
import entity.node.NumericDerivative;
import entity.node.PointDerivative;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExactDerivativeTest {
    private final Map<String, UserFunction> functions = new HashMap<>();
    private final ExactDerivative exact = new ExactDerivative();

    private Node parse(String latex) {
        return new MathParser(MathLexer.tokenize(latex), functions).parse();
    }

    private void define(String name, String body) {
        functions.put(name, new UserFunction(name, List.of("t"), parse(body), body));
    }

    private Context at(double x) {
        return new Context(functions).withVariable("x", x);
    }

    @Test
    void exactValueWithNoError() {
        Node body = parse("x^3");
        assertTrue(exact.isExact(body, "x", 2, at(0)));
        assertArrayEquals(new double[] {12, 0}, exact.at(body, "x", 2, at(2)));
        assertArrayEquals(new double[] {6, 0}, exact.at(body, "x", 3, at(2)));
    }

    @Test
    void cantWriteItOutMeansNull() {
        Node body = parse("|x|");
        assertFalse(exact.isExact(body, "x", 1, at(0)));
        assertNull(exact.at(body, "x", 1, at(1)));
    }

    @Test
    void undefinedBodyOrFormulaIsNaN() {
        assertTrue(Double.isNaN(exact.at(parse("\\sqrt{x}"), "x", 1, at(0))[0]));
        assertTrue(Double.isNaN(exact.at(parse("\\ln x"), "x", 1, at(-1))[0]));
        assertTrue(Double.isNaN(exact.at(parse("x^{3/2}"), "x", 1, at(0))[0]));
    }

    @Test
    void floatNoiseIsZeroAndFarOutIsLeftToRidders() {
        assertEquals(0, exact.at(parse("1000000\\sin x"), "x", 1, at(Math.PI / 2))[0]);
        assertNull(exact.at(parse("\\sin x"), "x", 1, at(1e12)));
    }

    @Test
    void redefinedCalleeIsNotServedFromTheCache() {
        define("g", "t^2");
        Node body = parse("g(x)");
        assertEquals(6, exact.at(body, "x", 1, at(3))[0]);
        define("g", "t^3");
        assertEquals(27, exact.at(body, "x", 1, at(3))[0]);
        define("g", "|t|");
        assertNull(exact.at(body, "x", 1, at(3)));
    }

    @Test
    void fallbackUsesRiddersWhenTheFormulaCant() {
        PointDerivative both = new FallbackDerivative(exact, new NumericDerivative());
        Node abs = parse("|x|");
        assertFalse(both.isExact(abs, "x", 1, at(0)));
        double[] numeric = both.at(abs, "x", 1, at(2));
        assertEquals(1, numeric[0], 1e-9);
        assertTrue(both.isExact(parse("x^2"), "x", 1, at(0)));
    }
}
