package use_case.evaluate;

import entity.MathLexer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormulaShapeTest {
    private static FormulaShape shape(String latex) {
        return FormulaShape.of(MathLexer.tokenize(latex));
    }

    @Test
    void trailingEquals() {
        assertEquals(FormulaShape.ENDS_WITH_EQUALS, shape("12\\times3+4="));
        assertEquals(FormulaShape.ENDS_WITH_EQUALS, shape("\\frac{="));
    }

    @Test
    void relations() {
        assertEquals(FormulaShape.RELATION, shape("x^{2}-5x+6=0"));
        assertEquals(FormulaShape.RELATION, shape("x^{2}<4"));
        assertEquals(FormulaShape.RELATION, shape("x\\le 3"));
        assertEquals(FormulaShape.RELATION, shape("\\{1,2\\}\\supseteq\\{1\\}"));
    }

    @Test
    void plain() {
        assertEquals(FormulaShape.PLAIN, shape("2+3"));
    }

    @Test
    void indexInsideASumIsNotARelation() {
        assertEquals(FormulaShape.PLAIN, shape("\\sum_{i=1}^{10} i"));
        assertEquals(FormulaShape.PLAIN, shape("\\prod_{k=1}^{5} k"));
        assertEquals(FormulaShape.ENDS_WITH_EQUALS, shape("\\sum_{i=1}^{10} i="));
        assertEquals(FormulaShape.RELATION, shape("\\sum_{i=1}^{n} i=10"));
    }
}
