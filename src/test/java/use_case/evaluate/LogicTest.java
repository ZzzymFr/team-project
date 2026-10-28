package use_case.evaluate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogicTest {
    @Test
    void connectives() {
        assertEquals("false", Calc.once("\\top\\land\\bot"));
        assertEquals("true", Calc.once("\\top\\lor\\bot"));
        assertEquals("false", Calc.once("\\neg\\top"));
        assertEquals("true", Calc.once("\\bot\\Rightarrow\\bot"));
        assertEquals("false", Calc.once("\\top\\Rightarrow\\bot"));
        assertEquals("false", Calc.once("\\top\\oplus\\top"));
        assertEquals("true", Calc.once("\\top\\Leftrightarrow\\top"));
        assertEquals("false", Calc.once("\\top\\rightarrow\\bot"));
        assertEquals("true", Calc.once("\\bot\\leftrightarrow\\bot"));
    }

    @Test
    void precedence() {
        assertEquals("true", Calc.once("\\top\\lor\\top\\land\\bot"));
        assertEquals("true", Calc.once("\\bot\\Rightarrow\\bot\\Rightarrow\\bot"));
        assertEquals("true", Calc.once("1<2\\land3>2"));
        assertEquals("true", Calc.once("\\neg1>2"));
        assertEquals("true", Calc.once("\\neg(1>2\\land2>1)"));
    }

    @Test
    void logicNeedsTruthValues() {
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("3\\land2")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("\\top+1")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("1\\oplus2")));
        assertEquals("Uncalculable", Calc.withoutReason(Calc.once("2\\rightarrow3")));
    }
}
