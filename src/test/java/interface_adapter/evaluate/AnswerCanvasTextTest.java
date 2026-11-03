package interface_adapter.evaluate;

import interface_adapter.AnswerKind;
import interface_adapter.TextRun;
import org.junit.jupiter.api.Test;
import use_case.evaluate.FormulaShape;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerCanvasTextTest {

    @Test
    void shapePicksTheSpot() {
        assertEquals(AnswerKind.AFTER_EQUALS, AnswerKind.of(FormulaShape.ENDS_WITH_EQUALS));
        assertEquals(AnswerKind.BELOW, AnswerKind.of(FormulaShape.RELATION));
        assertEquals(AnswerKind.RIGHT, AnswerKind.of(FormulaShape.PLAIN));
        assertTrue(AnswerKind.BELOW.isBelow());
        assertFalse(AnswerKind.AFTER_EQUALS.isBelow());
    }

    @Test
    void trailingEqualsDropsItsEquals() {
        assertEquals("40", AnswerFormatter.canvasText(AnswerKind.AFTER_EQUALS, "= 40"));
        assertEquals("= 5", AnswerFormatter.canvasText(AnswerKind.RIGHT, "= 5"));
    }

    @Test
    void minusIsTypeset() {
        assertEquals("−2 < x < 2", AnswerFormatter.canvasText(AnswerKind.BELOW, "-2 < x < 2"));
        assertEquals("−3", AnswerFormatter.canvasText(AnswerKind.AFTER_EQUALS, "= -3"));
    }

    @Test
    void runsGetEqualsInFront() {
        List<TextRun> runs = List.of(new TextRun("3x", false), new TextRun("2", true));
        assertEquals(runs, AnswerFormatter.canvasRuns(AnswerKind.AFTER_EQUALS, runs));
        assertEquals(List.of(new TextRun("= 3x", false), new TextRun("2", true)),
                AnswerFormatter.canvasRuns(AnswerKind.RIGHT, runs));
        assertEquals(List.of(new TextRun("= ", false), new TextRun("2", true)),
                AnswerFormatter.canvasRuns(AnswerKind.BELOW, List.of(new TextRun("2", true))));
    }
}
