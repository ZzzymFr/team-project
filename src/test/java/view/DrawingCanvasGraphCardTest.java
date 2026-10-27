package view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The graph card: shown beside the formula, never over the whole canvas, closable. */
class DrawingCanvasGraphCardTest {
    private static final BufferedImage CHART = new BufferedImage(1600, 1000, BufferedImage.TYPE_INT_RGB);

    private final DrawingCanvas canvas = new DrawingCanvas();

    @BeforeEach
    void sizeTheCanvas() {
        canvas.setSize(900, 500);
    }

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
    void cardSitsRightOfTheFormulaAndDoesNotFillTheCanvas() {
        drag(100, 200, 200, 250);
        canvas.showGraph(CHART);

        assertTrue(canvas.hasGraph());
        Rectangle2D card = canvas.getGraphCardOnScreen();
        assertTrue(card.getMinX() >= 200 + 40, "to the right of the ink, with a gap");
        assertEquals(0.4 * 900 + 20, card.getWidth(), 1, "40% of the canvas width plus its white border");
        assertTrue(card.getWidth() < 900 / 2.0 && card.getHeight() < 500, "never the whole canvas");
        assertEquals(1000.0 / 1600, (card.getHeight() - 20) / (card.getWidth() - 20), 0.01, "the chart keeps its shape");
        assertTrue(card.getMinY() >= 0 && card.getMaxY() <= 500, "the card is in view");
    }

    @Test
    void cardGoesBesideTheFormulaSelectedWithTheLasso() {
        drag(100, 100, 200, 130);   // formula A
        drag(100, 300, 250, 330);   // formula B
        canvas.setTool(DrawingCanvas.Tool.LASSO);
        drag(80, 280, 270, 280, 270, 350, 80, 350);   // circle B

        canvas.showGraph(CHART);

        Rectangle2D card = canvas.getGraphCardOnScreen();
        assertTrue(card.getMinX() >= 250 + 40, "right of B");
        assertEquals(315, card.getCenterY(), 2, "level with B, not with all the ink");
    }

    @Test
    void cardGoesBesideTheFormulaItIsGiven() {
        drag(100, 100, 200, 130);
        canvas.showGraph(CHART, new java.awt.Rectangle(400, 200, 50, 50));
        Rectangle2D card = canvas.getGraphCardOnScreen();
        assertTrue(card.getMinX() >= 450 + 40);
        assertEquals(225, card.getCenterY(), 2);
    }

    @Test
    void viewMovesSoACardNearTheTopIsFullyVisible() {
        drag(100, 20, 200, 40);   // formula at the very top: a centred card would stick out above
        canvas.showGraph(CHART);
        Rectangle2D card = canvas.getGraphCardOnScreen();
        assertTrue(card.getMinY() >= 59, "moved down, clear of the floating controls");
    }

    @Test
    void cardMovesWithTheView() {
        drag(100, 200, 200, 250);
        canvas.showGraph(CHART);
        double before = canvas.getGraphCardOnScreen().getMinX();
        canvas.panBy(30, 0);
        assertEquals(before + 30, canvas.getGraphCardOnScreen().getMinX(), 1e-9);
    }

    @Test
    void cardIsNotPartOfTheFormulaImage() {
        drag(100, 200, 200, 250);
        BufferedImage before = canvas.toFormulaImage();
        canvas.showGraph(CHART);
        BufferedImage after = canvas.toFormulaImage();
        assertEquals(before.getWidth(), after.getWidth());
        assertEquals(before.getHeight(), after.getHeight());
    }

    @Test
    void closeButtonRemovesTheCardWithoutDrawing() {
        drag(100, 200, 200, 250);
        canvas.showGraph(CHART);
        Point2D close = canvas.getGraphCloseOnScreen();

        drag((int) close.getX(), (int) close.getY(), (int) close.getX() + 5, (int) close.getY() + 5);

        assertFalse(canvas.hasGraph());
        assertNull(canvas.getGraphCardOnScreen());
        assertEquals(1, canvas.getStrokes().size(), "clicking × drew nothing");
    }

    @Test
    void withNoInkTheCardIsCentred() {
        canvas.showGraph(CHART);
        Rectangle2D card = canvas.getGraphCardOnScreen();
        assertEquals(450, card.getCenterX(), 1);
        assertEquals(250, card.getCenterY(), 1);
    }
}
