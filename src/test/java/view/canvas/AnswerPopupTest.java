package view.canvas;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerPopupTest {
    private final AnswerPopup popup = new AnswerPopup(g -> { });

    @Test
    void formulaIsTypesetWithCalculate() {
        AtomicInteger clicks = new AtomicInteger();
        popup.showFormula("\\frac{1}{2}", clicks::incrementAndGet);
        assertTrue(popup.isTypeset());
        assertTrue(popup.hasCalculate());
        assertEquals("\\frac{1}{2}", popup.getLatex());
        popup.clickCalculate();
        assertEquals(1, clicks.get());
    }

    @Test
    void badLatexFallsBackToText() {
        popup.showFormula("\\left(", () -> { });
        assertFalse(popup.isTypeset());
        assertTrue(popup.hasCalculate());
        assertEquals("\\left(", popup.getLatex());
    }

    @Test
    void problemHasNoCalculate() {
        popup.showProblem("Handwriting recognition is not connected yet.");
        assertFalse(popup.hasCalculate());
        assertEquals("Handwriting recognition is not connected yet.", popup.getProblem());
    }

    @Test
    void preferredSizeFitsButtonAndCard() {
        popup.showFormula("1+1", () -> { });
        Dimension small = popup.getPreferredSize();
        popup.showFormula("1+1+1+1+1+1+1+1+1+1+1+1", () -> { });
        Dimension big = popup.getPreferredSize();
        assertTrue(small.width > 0 && small.height > 0);
        assertTrue(big.width > small.width);
    }
}
