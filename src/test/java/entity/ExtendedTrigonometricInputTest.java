package entity;

import org.junit.jupiter.api.Test;
import entity.value.Values;
import java.util.HashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ExtendedTrigonometricInputTest {
    private static double evaluate(String text) {
        return Values.toNumber(new MathParser(MathLexer.tokenize(text), new HashMap<>())
                .parse().evaluate(new Context(new HashMap<>())));
    }
    @Test void recognizesPlainTrigonometricFunctions() {
        assertEquals(List.of("\\cot", "(", "x", ")", "+", "\\sec", "(", "x", ")"),
                MathLexer.tokenize("cot(x)+sec(x)"));
    }
    @Test void recognizesInverseTrigonometricFunctions() {
        assertEquals(List.of("\\arcsin", "(", "x", ")", "+", "\\arctan", "(", "x", ")"),
                MathLexer.tokenize("arcsin(x)+arctan(x)"));
    }
    @Test void recognizesHyperbolicAndExponential() {
        assertEquals("\\sinh", MathLexer.tokenize("sinh(1)").get(0));
        assertEquals("\\tanh", MathLexer.tokenize("tanh(1)").get(0));
        assertEquals("\\exp", MathLexer.tokenize("exp(1)").get(0));
    }
    @Test void tanOfZeroIsZero() { assertEquals(0.0, evaluate("tan(0)"), 1e-10); }
    @Test void cotOfPiOverFourIsOne() { assertEquals(1.0, evaluate("cot(pi/4)"), 1e-8); }
    @Test void arcsinOfOneIsPiOverTwo() { assertEquals(Math.PI / 2, evaluate("arcsin(1)"), 1e-9); }
    @Test void arctanOfOneIsPiOverFour() { assertEquals(Math.PI / 4, evaluate("arctan(1)"), 1e-9); }
    @Test void secOfZeroIsOne() { assertEquals(1.0, evaluate("sec(0)"), 1e-9); }
    @Test void plainAndLatexSyntaxEvaluateEqually() {
        assertEquals(evaluate("\\arccos(0)"), evaluate("arccos(0)"), 1e-9);
    }
}
