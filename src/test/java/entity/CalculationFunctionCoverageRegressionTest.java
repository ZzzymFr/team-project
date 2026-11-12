package entity;

import entity.value.Values;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Existing supported calculator functions should stay executable in both input styles. */
class CalculationFunctionCoverageRegressionTest {
    private static double eval(String expression) {
        return Values.toNumber(new MathParser(MathLexer.tokenize(expression),new HashMap<>())
                .parse().evaluate(new Context(new HashMap<>())));
    }
    @Test void allCommonFunctionNamesGetDistinctTokens() {
        for(String fn:List.of("sin","cos","tan","cot","sec","csc","arcsin","arccos",
                "arctan","sinh","cosh","tanh","exp","ln","log","sqrt")) {
            assertEquals("\\"+fn,MathLexer.tokenize(fn+"(1)").get(0),fn);
        }
    }
    @Test void inverseTrigCoversPositiveNegativeAndZero() {
        assertEquals(Math.PI/4,eval("arctan(1)"),1e-9);
        assertEquals(-Math.PI/4,eval("arctan(-1)"),1e-9);
        assertEquals(0,eval("arcsin(0)"),1e-9);
        assertEquals(Math.PI/2,eval("arccos(0)"),1e-9);
    }
    @Test void reciprocalTrigonometryUsesRadians() {
        assertEquals(1,eval("cot(pi/4)"),1e-8);
        assertEquals(1,eval("sec(0)"),1e-8);
        assertEquals(1,eval("csc(pi/2)"),1e-8);
    }
    @Test void latexAndPlainFunctionCommandsMatch() {
        assertEquals(eval("arcsin(0.5)"),eval("\\arcsin(0.5)"),1e-9);
        assertEquals(eval("tan(0.25)"),eval("\\tan(0.25)"),1e-9);
    }
    @Test void hyperbolicAndExpRemainAvailable() {
        assertEquals(0,eval("sinh(0)"),1e-9);
        assertEquals(1,eval("cosh(0)"),1e-9);
        assertEquals(0,eval("tanh(0)"),1e-9);
        assertEquals(Math.E,eval("exp(1)"),1e-9);
    }
}
