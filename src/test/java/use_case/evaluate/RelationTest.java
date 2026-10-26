package use_case.evaluate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RelationTest {
    @Test
    void comparisonsGiveTruthValues() {
        assertEquals("true", Calc.once("3<5"));
        assertEquals("false", Calc.once("3>5"));
        assertEquals("true", Calc.once("2\\leq2"));
        assertEquals("true", Calc.once("3\\geq2"));
        assertEquals("false", Calc.once("2\\neq2"));
        assertEquals("true", Calc.once("0.1+0.2\\leq0.3"));
    }

    @Test
    void chains() {
        assertEquals("true", Calc.once("1<2<3"));
        assertEquals("false", Calc.once("3<2<5"));
        assertEquals("true", Calc.once("1+1=2=4-2"));
        assertEquals("false", Calc.once("1=1=2"));
        assertEquals("Please use only one \"=\".", Calc.once("x=y=3"));
    }

    @Test
    void approximately() {
        assertEquals("true", Calc.once("\\pi\\approx3.14"));
        assertEquals("false", Calc.once("\\pi\\approx3.15"));
        assertEquals("true", Calc.once("\\pi\\approx3"));
        assertEquals("true", Calc.once("3.14\\approx\\pi"));
        assertEquals("true", Calc.once("\\sqrt{2}\\approx\\frac{99}{70}"));
    }

    @Test
    void notEqualWithUnknown() {
        assertEquals("x ≠ 3", Calc.once("x\\neq3"));
        assertEquals("x ≠ -2 and x ≠ 2", Calc.once("x^2\\neq4"));
        assertEquals("true for all x", Calc.once("x^2\\neq-1"));
    }

    @Test
    void infinityEqualsInfinity() {
        assertEquals("true", Calc.once("\\infty=\\infty+1"));
        assertEquals("false", Calc.once("\\infty=-\\infty"));
    }

    @Test
    void orderingNeedsNumbers() {
        assertEquals("Uncalculable", Calc.once("\\top<1"));
    }
}
