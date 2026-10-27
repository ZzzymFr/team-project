package entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerKindTest {

    @Test
    void trailingEquals() {
        assertEquals(AnswerKind.AFTER_EQUALS, AnswerKind.of("12\\times3+4="));
        assertEquals("40", AnswerKind.AFTER_EQUALS.answerText("= 40"));
        assertFalse(AnswerKind.AFTER_EQUALS.isBelow());
    }

    @Test
    void equationGoesBelow() {
        assertEquals(AnswerKind.BELOW, AnswerKind.of("x^{2}-5x+6=0"));
        assertEquals(AnswerKind.BELOW, AnswerKind.of("x^{2}<4"));
        assertEquals(AnswerKind.BELOW, AnswerKind.of("x\\le 3"));
        assertTrue(AnswerKind.BELOW.isBelow());
    }

    @Test
    void plainExpressionKeepsItsEquals() {
        assertEquals(AnswerKind.RIGHT, AnswerKind.of("2+3"));
        assertEquals("= 5", AnswerKind.RIGHT.answerText("= 5"));
    }

    @Test
    void minusIsTypeset() {
        assertEquals("−2 < x < 2", AnswerKind.BELOW.answerText("-2 < x < 2"));
        assertEquals("−3", AnswerKind.AFTER_EQUALS.answerText("= -3"));
    }

    @Test
    void unreadableLatexStillClassified() {
        assertEquals(AnswerKind.AFTER_EQUALS, AnswerKind.of("\\frac{="));
        assertEquals(AnswerKind.BELOW, AnswerKind.of("\\text{a}=1"));  // lexer throws
        assertEquals(AnswerKind.RIGHT, AnswerKind.of(null));
    }
}
