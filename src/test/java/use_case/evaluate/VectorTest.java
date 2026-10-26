package use_case.evaluate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VectorTest {
    @Test
    void arithmetic() {
        assertEquals("= (4, 6)", Calc.once("(1,2)+(3,4)"));
        assertEquals("= (0, 0)", Calc.once("(1,2)-(1,2)"));
        assertEquals("= (2, 4)", Calc.once("2(1,2)"));
        assertEquals("= (-1, -2)", Calc.once("-(1,2)"));
    }

    @Test
    void products() {
        assertEquals("= 11", Calc.once("(1,2)\\cdot(3,4)"));
        assertEquals("= 11", Calc.once("\\langle1,2\\rangle\\cdot\\langle3,4\\rangle"));
        assertEquals("= 11", Calc.once("\\langle(1,2),(3,4)\\rangle"));
        assertEquals("= (0, 0, 1)", Calc.once("(1,0,0)\\times(0,1,0)"));
    }

    @Test
    void norms() {
        assertEquals("= 5", Calc.once("\\|(3,4)\\|"));
        assertEquals("= 5", Calc.once("|(3,4)|"));
        assertEquals("= (0.6, 0.8)", Calc.once("\\hat{(3,4)}"));
    }

    @Test
    void relations() {
        assertEquals("true", Calc.once("(1,0)\\perp(0,1)"));
        assertEquals("false", Calc.once("(1,1)\\perp(0,1)"));
        assertEquals("true", Calc.once("(1,2)\\in\\mathbb{R}^2"));
        assertEquals("false", Calc.once("(1,2)\\in\\mathbb{R}^{3}"));
        assertEquals("true", Calc.once("(1,2)=(1,2)"));
    }

    @Test
    void errorsAndScalars() {
        assertEquals("Uncalculable", Calc.once("(1,2)+(1,2,3)"));
        assertEquals("Uncalculable", Calc.once("(1,2)\\times(3,4)"));
        assertEquals("Uncalculable", Calc.once("(1,0/0)"));
        assertEquals("Uncalculable", Calc.once("0/0\\in\\mathbb{R}"));
        assertEquals("= (1, 2)", Calc.once("(2,4)/2"));
        assertEquals("Uncalculable", Calc.once("(2,4)/0"));
        assertEquals("Uncalculable", Calc.once("(1,2)^2"));
        assertEquals("Uncalculable", Calc.once("2/(1,2)"));
        assertEquals("Uncalculable", Calc.once("10^{308}(10,1)"));
        assertEquals("Uncalculable", Calc.once("10^{308}(1,0,0)\\times(0,10^{308},0)+(1,1,1)"));
        assertEquals("= 12", Calc.once("3\\cdot4"));
        assertEquals("= 12", Calc.once("3\\times4"));
        assertEquals("= 3", Calc.once("(3)"));
    }
}
