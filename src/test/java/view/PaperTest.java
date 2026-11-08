package view;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** A4 sheets stacked like Word pages. */
class PaperTest {
    private static final int PITCH = Paper.PAGE_HEIGHT + Paper.PAGE_GAP;

    @Test
    void sheetsAreA4StackedWithAGap() {
        assertEquals(new Rectangle(0, 0, 794, 1123), Paper.page(0));
        assertEquals(new Rectangle(0, PITCH, 794, 1123), Paper.page(1));
    }

    @Test
    void oneSheetWhenBlankThenOneBlankSheetAfterTheInk() {
        assertEquals(1, Paper.pageCount(null));
        assertEquals(2, Paper.pageCount(new Rectangle(10, 10, 50, 50)));
        assertEquals(3, Paper.pageCount(new Rectangle(10, PITCH + 10, 50, 50)));
    }

    @Test
    void pointsInGapsBesideTheSheetsOrPastTheLastAreOffThePaper() {
        assertEquals(0, Paper.pageAt(0, 0, 1));
        assertEquals(-1, Paper.pageAt(-1, 10, 1));
        assertEquals(-1, Paper.pageAt(794, 10, 1));
        assertEquals(-1, Paper.pageAt(10, 1123 + 5, 2));       // in the gap
        assertEquals(1, Paper.pageAt(10, PITCH + 5, 2));
        assertEquals(-1, Paper.pageAt(10, PITCH + 5, 1));      // that sheet is not there yet
        assertEquals(-1, Paper.pageAt(10, -5, 1));
    }

    @Test
    void movesStayOnTheSheetTheBoxLandsOver() {
        Rectangle box = new Rectangle(100, 100, 50, 50);
        assertArrayEquals(new int[]{643, 0}, Paper.keepOnPaper(box, 1000, 0, 1));
        assertArrayEquals(new int[]{-100, -100}, Paper.keepOnPaper(box, -500, -500, 1));
        assertArrayEquals(new int[]{0, PITCH}, Paper.keepOnPaper(box, 0, PITCH, 2));
        assertArrayEquals(new int[]{0, 972}, Paper.keepOnPaper(box, 0, PITCH, 1), "no second sheet yet");
    }

    @Test
    void resizeStopsAtTheEdgeAroundItsAnchor() {
        Rectangle box = new Rectangle(600, 100, 100, 100);
        assertEquals(1.93, Paper.keepOnPaper(box, 600, 100, 5, 1), 1e-9);   // right edge 600 + 193 = 793
        assertEquals(0.5, Paper.keepOnPaper(box, 600, 100, 0.5, 1), 1e-9);  // shrinking is fine
        Rectangle offPaper = new Rectangle(900, 100, 10, 10);
        assertEquals(5, Paper.keepOnPaper(offPaper, 900, 100, 5, 1), 1e-9);
    }

    @Test
    void clampKeepsAPointOnItsSheet() {
        assertArrayEquals(new int[]{793, 100}, Paper.clampTo(0, 900, 100));
        assertArrayEquals(new int[]{0, PITCH}, Paper.clampTo(1, -20, 1130));
    }
}
