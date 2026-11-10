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
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("x>1\\pm1")));
    }

    @Test
    void flatRootsAndDomainEdges() {
        assertEquals("x ≥ 0", Calc.once("x^5\\geq0"));
        assertEquals("x = 0", Calc.once("x^6\\leq0"));
        assertEquals("x ≥ 1", Calc.once("(x-1)^5\\geq0"));
        assertEquals("x > 0", Calc.once("x^7>0"));
        assertEquals("0 ≤ x ≤ 0.003", Calc.once("x\\leq0.003\\land x^5\\geq0"));
        assertEquals("x ≥ 0.01", Calc.once("\\sqrt{x-0.005}\\geq0"));
        assertEquals("x ≤ 0", Calc.once("x^3\\leq0"));
        assertEquals("-0.0001 ≤ x ≤ 0.0001", Calc.once("x^2\\leq0.00000001"));
    }

    @Test
    void boundariesPastTheRange() {
        assertEquals("x > 150", Calc.once("2x>300"));
        assertEquals("x ≥ 150", Calc.once("x\\geq 150"));
        assertEquals("x > 500", Calc.once("x-500>0"));
        assertEquals("x > 200", Calc.once("0.05x>10"));
        assertEquals("x > 200", Calc.once("25x-5000>0"));
        assertEquals("x < 101", Calc.once("x<101"));
        assertEquals("x > 100.5", Calc.once("x>100.5"));
        assertEquals("x < -100.01", Calc.once("x<-100.01"));
        assertEquals("x > 1000", Calc.once("x>1000"));
        assertEquals("x < -5000", Calc.once("x<-5000"));
        assertEquals("x > 1000000000000", Calc.once("x>10^{12}"));
    }

    @Test
    void rangesPastTheRange() {
        assertEquals("-200 < x < 200", Calc.once("|x|<200"));
        assertEquals("-200 < x < 200", Calc.once("x^2<40000"));
        assertEquals("x < -10000 or x > 10000", Calc.once("x^2>10^8"));
        assertEquals("50 < x < 150", Calc.once("50<x<150"));
        assertEquals("50 < x < 150", Calc.once("x>50 \\land x<150"));
        assertEquals("x < 0 or x > 1000", Calc.once("\\frac{1}{x}<0.001"));
    }
}
