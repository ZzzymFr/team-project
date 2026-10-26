package use_case.evaluate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariableAndConstantTest {
    @Test
    void newVariableNames() {
        assertEquals("α = 2", Calc.once("\\alpha+1=3"));
        assertEquals("x_1 = 2", Calc.once("x_1+1=3"));
        assertEquals("x_{n1} = 5", Calc.once("x_{n1}=5"));
        assertEquals("\\vec{v} = 2", Calc.once("\\vec{v}+1=3"));
        assertEquals("? = 2", Calc.once("3+?=5"));
    }

    @Test
    void implicitMultiplicationWithNewNames() {
        assertEquals("r = 1", Calc.once("2\\pi r=2\\pi"));
        assertEquals("x_2 = 3", Calc.once("2x_2=6"));
        assertEquals("\\vec{v} = 2", Calc.once("3\\vec{v}=6"));
        assertTrue(Calc.once("\\alpha\\beta=1").contains("more than one unknown"));
        assertTrue(Calc.once("x_1x_2=1").contains("more than one unknown"));
    }

    @Test
    void percentAndInfinity() {
        assertEquals("= 0.5", Calc.once("50\\%"));
        assertEquals("= 10", Calc.once("20%\\times50"));
        assertEquals("= ∞", Calc.once("\\infty+1"));
        assertEquals("= -∞", Calc.once("-\\infty"));
        assertEquals("= undefined", Calc.once("\\infty-\\infty"));
        assertEquals("= 0", Calc.once("1/\\infty"));
    }

    @Test
    void plusMinus() {
        assertEquals("= 3 or -1", Calc.once("1\\pm2"));
        assertEquals("= 1 or -1", Calc.once("\\pm1"));
        assertEquals("= 2", Calc.once("2\\pm0"));
        assertEquals("x = -1 or x = 3", Calc.once("x=1\\pm2"));
        assertEquals("Uncalculable", Calc.once("1\\pm1\\pm1\\pm1\\pm1\\pm1"));
    }

    @Test
    void truthConstants() {
        assertEquals("true", Calc.once("\\top"));
        assertEquals("false", Calc.once("\\bot"));
    }

    @Test
    void infinityInsideFunctionBody() {
        Calc calc = new Calc();
        calc.show("f(x)=\\infty+x");
        assertEquals("= ∞", calc.show("f(1)"));
    }

    @Test
    void plusMinusInFunctionDefinitionIsUncalculable() {
        assertEquals("Uncalculable", Calc.once("f(x)=x\\pm1"));
    }
}
