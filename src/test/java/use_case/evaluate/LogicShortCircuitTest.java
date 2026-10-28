package use_case.evaluate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogicShortCircuitTest {
    @Test
    void decidedLeftSideSkipsUndefinedRightSide() {
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, x\\neq0\\Rightarrow\\frac{x}{x}=1"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, x\\geq0\\Rightarrow\\sqrt{x}\\geq0"));
        assertEquals("true", Calc.once("\\forall x\\in\\mathbb{R}, x=0\\lor\\frac{1}{x}\\neq0"));
        assertEquals("x ≥ 0", Calc.once("x=0\\lor\\frac{1}{x}>0"));
        assertEquals("true for all x", Calc.once("x>0\\Rightarrow\\frac{1}{x}>0"));
        assertEquals("false", Calc.once("\\forall x\\in\\mathbb{R}, \\frac{1}{x}\\neq0"));
    }
}
