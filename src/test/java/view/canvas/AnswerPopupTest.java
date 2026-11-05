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
        popup.showFormula("\\frac{1}{2}", null, clicks::incrementAndGet);
        assertTrue(popup.isTypeset());
        assertTrue(popup.hasCalculate());
        assertEquals("\\frac{1}{2}", popup.getLatex());
        popup.clickCalculate();
        assertEquals(1, clicks.get());
    }

    @Test
    void graphAndCalculateAreOfferedTogether() {
        AtomicInteger graphs = new AtomicInteger();
        AtomicInteger calculations = new AtomicInteger();
        popup.showFormula("y=x", graphs::incrementAndGet, calculations::incrementAndGet);
        assertTrue(popup.canGraph());
        assertTrue(popup.hasCalculate());

        popup.clickGraph();
        assertEquals(1, graphs.get());
        assertEquals(0, calculations.get());
        popup.clickCalculate();
        assertEquals(1, calculations.get());
    }

    @Test
    void withoutAGrapherGraphIsGreyedOutAndDoesNothing() {
        popup.showFormula("y=x", null, () -> { });
        assertFalse(popup.canGraph());
        popup.clickGraph();  // must not throw
    }

    @Test
    void theCapsuleStaysInsideTheCanvasNearTheTopLeftCorner() {
        popup.showFormula("1+1", null, () -> { });
        java.awt.Rectangle spot = popup.spotBeside(new java.awt.Rectangle(0, 0, 10, 10), 900, 560);
        assertTrue(spot.x + view.glass.GlassBar.MARGIN >= 0, "x " + spot.x);
        assertTrue(spot.y + view.glass.GlassBar.MARGIN >= 0, "y " + spot.y);
    }

    @Test
    void badLatexFallsBackToText() {
        popup.showFormula("\\left(", null, () -> { });
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
        popup.showFormula("1+1", null, () -> { });
        Dimension small = popup.getPreferredSize();
        popup.showFormula("1+1+1+1+1+1+1+1+1+1+1+1", null, () -> { });
        Dimension big = popup.getPreferredSize();
        assertTrue(small.width > 0 && small.height > 0);
        assertTrue(big.width > small.width);
    }
}
