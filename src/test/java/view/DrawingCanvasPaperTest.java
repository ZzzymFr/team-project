package view;

import entity.InkPoint;
import org.junit.jupiter.api.Test;

import java.awt.event.MouseEvent;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pen only writes on the A4 sheets. */
class DrawingCanvasPaperTest {
    private static final int SECOND_PAGE_TOP = Paper.PAGE_HEIGHT + Paper.PAGE_GAP;
    private final DrawingCanvas canvas = new DrawingCanvas();

    private void drag(int... xy) {
        send(MouseEvent.MOUSE_PRESSED, xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) {
            send(MouseEvent.MOUSE_DRAGGED, xy[i], xy[i + 1]);
        }
        send(MouseEvent.MOUSE_RELEASED, xy[xy.length - 2], xy[xy.length - 1]);
    }

    private void send(int id, int x, int y) {
        canvas.dispatchEvent(new MouseEvent(canvas, id, 0, 0, x, y, 1, false, MouseEvent.BUTTON1));
    }

    @Test
    void writingOffThePaperDrawsNothing() {
        drag(-20, 100, -10, 120);                         // beside the sheet
        drag(900, 100, 950, 120);
        drag(100, Paper.PAGE_HEIGHT + 10, 150, Paper.PAGE_HEIGHT + 12);  // in the gap below it
        assertTrue(canvas.isBlank());
        assertTrue(!canvas.canUndo(), "nothing to undo either");
    }

    @Test
    void aStrokeThatRunsOffTheSheetEndsAtItsEdge() {
        drag(700, 100, 780, 100, 900, 100, 700, 200);
        List<InkPoint> points = canvas.getStrokes().get(0).getPoints();
        assertEquals(3, points.size(), "points after it left the sheet are dropped");
        assertEquals(793, points.get(2).getX());
        assertEquals(100, points.get(2).getY());
    }

    private java.awt.Rectangle inkBounds() {
        return canvas.getAllInkBounds();
    }

    private void lassoAround(int x0, int y0, int x1, int y1) {
        canvas.setTool(DrawingCanvas.Tool.LASSO);
        drag(x0, y0, x1, y0, x1, y1, x0, y1);
    }

    @Test
    void aLassoMoveStopsAtTheEdgeOfTheSheet() {
        drag(20, 20, 40, 40);
        lassoAround(0, 0, 100, 100);
        drag(30, 30, 2000, 30);
        assertEquals(793, inkBounds().getMaxX(), 0, "pushed right up to the edge");
        assertEquals(20, inkBounds().y);

        drag(780, 30, 780, Paper.PAGE_HEIGHT);       // down into the gap under the sheet
        assertEquals(Paper.PAGE_HEIGHT - 1, inkBounds().getMaxY(), 0, "stays on the sheet");
    }

    @Test
    void aLassoMoveCanCarryInkToTheNextSheet() {
        drag(20, 20, 40, 40);
        lassoAround(0, 0, 100, 100);
        drag(30, 30, 30, SECOND_PAGE_TOP + 100);
        assertEquals(SECOND_PAGE_TOP + 90, inkBounds().y);
    }

    @Test
    void aLassoResizeStopsAtTheEdgeOfTheSheet() {
        drag(700, 100, 780, 180);
        lassoAround(690, 90, 790, 190);
        drag(788, 188, 1500, 900);                    // bottom-right handle, far past the edge
        assertEquals(793, inkBounds().getMaxX(), 1, "grown only as far as the edge");
        assertTrue(inkBounds().getMaxY() > 180, "but grown");
    }

    private java.awt.geom.Rectangle2D firstSheetOnScreen() {
        return canvas.worldToScreen(new java.awt.geom.Rectangle2D.Double(0, 0, Paper.PAGE_WIDTH, Paper.PAGE_HEIGHT));
    }

    @Test
    void keptInViewThePaperStaysCentredAcrossAndCannotScrollPastItsEnds() {
        canvas.setSize(900, 600);
        canvas.setKeepPaperInView(true);
        assertEquals((900 - Paper.PAGE_WIDTH) / 2.0, firstSheetOnScreen().getX(), 0.01, "centred across");

        canvas.panBy(-500, 300);
        assertEquals((900 - Paper.PAGE_WIDTH) / 2.0, firstSheetOnScreen().getX(), 0.01, "no sideways scroll when it fits");
        assertEquals(76, firstSheetOnScreen().getY(), 0.01, "not above the first sheet");

        canvas.panBy(0, -100_000);
        assertEquals(600 - 110, firstSheetOnScreen().getMaxY(), 0.01, "not past the bottom of the last sheet");
    }

    @Test
    void scrollingUpAndDownGoesFromTheTopOfTheFirstSheetToTheBottomOfTheLast() {
        canvas.setSize(900, 600);
        canvas.setKeepPaperInView(true);
        int extent = 600 - 110 - 76;
        assertEquals(extent, canvas.getScrollExtentY());
        assertEquals(Paper.PAGE_HEIGHT - extent, canvas.getScrollMaxY(), "one sheet, less what is in view");
        canvas.setScrollY(0);
        assertEquals(0, canvas.getScrollY());
        assertEquals(76, firstSheetOnScreen().getY(), 0.01, "the top of the first sheet");

        canvas.setScrollY(300);
        assertEquals(300, canvas.getScrollY());
        assertEquals(76 - 300, firstSheetOnScreen().getY(), 0.01);

        canvas.setScrollY(100_000);
        assertEquals(canvas.getScrollMaxY(), canvas.getScrollY(), "not past the last sheet");
        canvas.panBy(0, 50);
        assertEquals(canvas.getScrollMaxY() - 50, canvas.getScrollY(), "dragging the canvas scrolls too");

        canvas.setZoom(2);
        assertTrue(canvas.getScrollMaxY() > Paper.PAGE_HEIGHT, "more to scroll through when zoomed in");
    }

    @Test
    void zoomedInTheWidePaperScrollsSidewaysOnlyToItsEdges() {
        canvas.setSize(900, 600);
        canvas.setKeepPaperInView(true);
        canvas.setZoom(2);
        canvas.panBy(10_000, 0);
        assertEquals(16, firstSheetOnScreen().getX(), 0.01);
        canvas.panBy(-10_000, 0);
        assertEquals(900 - 16, firstSheetOnScreen().getMaxX(), 0.01);
    }

    @Test
    void aBlankSheetFollowsTheInkToWriteOn() {
        assertEquals(1, canvas.getPageCount());
        drag(100, SECOND_PAGE_TOP + 10, 150, SECOND_PAGE_TOP + 20);
        assertTrue(canvas.isBlank(), "the second sheet is not there before anything is written");

        drag(100, 100, 150, 120);
        assertEquals(2, canvas.getPageCount());
        drag(100, SECOND_PAGE_TOP + 10, 150, SECOND_PAGE_TOP + 20);
        assertEquals(2, canvas.getStrokes().size());
        assertEquals(3, canvas.getPageCount());
    }
}
