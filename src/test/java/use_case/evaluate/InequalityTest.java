package use_case.evaluate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InequalityTest {
    @Test
    void inequalitiesGiveIntervals() {
        assertEquals("x > 3", Calc.once("x>3"));
        assertEquals("-2 ≤ x ≤ 2", Calc.once("x^2\\leq4"));
        assertEquals("x < -2 or x > 2", Calc.once("x^2>4"));
        assertEquals("x > 0", Calc.once("\\frac{1}{x}>0"));
        assertEquals("x = 0", Calc.once("x^2\\leq0"));
        assertEquals("no solution", Calc.once("x^2<0"));
        assertEquals("true for all x", Calc.once("x^2\\geq0"));
        assertEquals("1 < x < 5", Calc.once("x>1\\land x<5"));
        assertEquals("1 < x ≤ 3", Calc.once("1<x\\leq3"));
        assertEquals("x ≥ 0", Calc.once("2x\\geq x"));
        assertEquals("x < 1 or x > 2", Calc.once("\\neg(1\\leq x\\leq2)"));
        assertEquals("Uncalculable", Calc.once("x>1\\pm1"));
    }
}
