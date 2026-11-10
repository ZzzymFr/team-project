package use_case.evaluate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SetTest {
    @Test
    void finiteSetsAndOperations() {
        assertEquals("= {1, 2, 3}", Calc.once("\\{2,1\\}\\cup\\{3\\}"));
        assertEquals("= {2}", Calc.once("\\{1,2\\}\\cap\\{2,3\\}"));
        assertEquals("= {}", Calc.once("\\{\\}"));
        assertEquals("= {2}", Calc.once("\\{1+1,\\sqrt{4}\\}"));
        assertEquals("= 3", Calc.once("|\\{1,2,3\\}|"));
    }

    @Test
    void membership() {
        assertEquals("true", Calc.once("3\\in\\{1,2,3\\}"));
        assertEquals("true", Calc.once("4\\notin\\{1,2,3\\}"));
        assertEquals("false", Calc.once("2.5\\in\\mathbb{Z}"));
        assertEquals("true", Calc.once("0\\in\\mathbb{N}"));
        assertEquals("false", Calc.once("-1\\in\\mathbb{N}"));
        assertEquals("true", Calc.once("\\pi\\in\\mathbb{R}"));
        assertEquals("false", Calc.once("\\infty\\in\\mathbb{R}"));
        assertEquals("true", Calc.once("0.5\\in\\mathbb{Q}"));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\pi\\in\\mathbb{Q}")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\sqrt{2}\\in\\mathbb{Q}")));
    }

    @Test
    void wholeNumbersDespiteFloatNoise() {
        assertEquals("true", Calc.once("\\sqrt{2}\\sqrt{2}\\in\\mathbb{Z}"));
        assertEquals("false", Calc.once("\\sqrt{2}\\sqrt{2}\\notin\\mathbb{Z}"));
        assertEquals("true", Calc.once("\\sqrt{2}^2\\in\\mathbb{N}"));
        assertEquals("true", Calc.once("\\sqrt{2}\\cdot\\sqrt{8}\\in\\mathbb{Z}"));
        assertEquals("true", Calc.once("\\sqrt{3}\\cdot\\sqrt{12}\\in\\mathbb{Z}"));
        assertEquals("true", Calc.once("\\sin(\\pi)\\in\\mathbb{Z}"));
        assertEquals("true", Calc.once("\\tan(\\pi/4)\\in\\mathbb{Z}"));
        assertEquals("true", Calc.once("2\\sin(\\pi/6)\\in\\mathbb{Z}"));
        assertEquals("true", Calc.once("e^{\\ln 2}\\in\\mathbb{Z}"));
        assertEquals("true", Calc.once("1000^{1/3}\\in\\mathbb{Z}"));
        assertEquals("true", Calc.once("\\log_{10}1000\\in\\mathbb{Z}"));
        assertEquals("true", Calc.once("1.1\\cdot 1.1\\cdot 100\\in\\mathbb{Z}"));
        assertEquals("true", Calc.once("4.35\\cdot 100\\in\\mathbb{Z}"));
        assertEquals("true", Calc.once("\\forall x\\in\\{1,2,3\\}, \\sqrt{x}^2\\in\\mathbb{Z}"));
    }

    @Test
    void nearWholeIsStillNotWhole() {
        assertEquals("false", Calc.once("0.999999999999\\in\\mathbb{Z}"));
        assertEquals("false", Calc.once("1000000.5\\in\\mathbb{Z}"));
        assertEquals("false", Calc.once("7/2\\in\\mathbb{Z}"));
        assertEquals("true", Calc.once("\\cos(\\pi)\\in\\mathbb{Z}"));
        assertEquals("true", Calc.once("\\pi/\\pi\\in\\mathbb{Z}"));
        assertEquals("false", Calc.once("\\sqrt{2}\\in\\mathbb{Z}"));
    }

    @Test
    void subsetAndEquality() {
        assertEquals("true", Calc.once("\\{1,2\\}\\subset\\{1,2,3\\}"));
        assertEquals("false", Calc.once("\\{1,4\\}\\subset\\{1,2,3\\}"));
        assertEquals("true", Calc.once("\\{1,2\\}\\subset\\mathbb{N}"));
        assertEquals("true", Calc.once("\\mathbb{N}\\subset\\mathbb{Z}"));
        assertEquals("false", Calc.once("\\mathbb{R}\\subset\\mathbb{Q}"));
        assertEquals("true", Calc.once("\\{1,2\\}=\\{2,1\\}"));
    }

    @Test
    void uncalculableCases() {
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\{1\\}+1")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\mathbb{R}")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\mathbb{N}\\cup\\{1\\}")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\{1/0\\}")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\{\\infty\\}")));
    }

    @Test
    void membershipInConditionSolving() {
        assertEquals("x = 1 or x = 2", Calc.once("x\\in\\{1,2\\}"));
        assertEquals("x = 0 or x = 1", Calc.once("x+1\\in\\{1,2\\}"));
    }
}
