package view;

import interface_adapter.AnswerKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import view.answer.AnswerInk;
import view.answer.CanvasAnswer;

import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// answer on the canvas acts like a piece of ink: colour, erase, select, move
class DrawingCanvasAnswerObjectTest {
    private static final Color RED = new Color(220, 38, 38);

    private final DrawingCanvas canvas = new DrawingCanvas();

    @BeforeEach
    void setUp() {
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

    // "1=" 40 tall, x 100..170, y 100..140, answered w/ text
    private CanvasAnswer answered(String text) {
        drag(100, 100, 100, 140);
        drag(130, 115, 170, 115);
        drag(130, 125, 170, 125);
        FormulaCapture c = canvas.takeFormulasToCalculate().get(0);
        assertTrue(canvas.showAnswer(c.getId(), c.getRevision(), AnswerKind.AFTER_EQUALS, text, false));
        return canvas.getAnswers().get(0);
    }

    @Test
    void answerIsInThePenColour() {
        canvas.setInkColor(RED);
        assertEquals(RED, answered("2").getColor());
    }

    @Test
    void defaultInkGivesBlackAnswer() {
        assertEquals(Color.BLACK, answered("2").getColor());
    }

    @Test
    void erasingTheEmptyPartOfItsBoxKeepsIt() {
        CanvasAnswer a = answered("1     1");
        canvas.setTool(DrawingCanvas.Tool.ERASER);
        int mid = (int) a.getBox().getCenterX();
        drag(mid, (int) a.getY() - 20, mid, (int) (a.getY() + a.getHeight()) + 20);
        assertEquals(1, canvas.getAnswers().size());
    }

    @Test
    void erasingItsInkRemovesTheWholeAnswer() {
        CanvasAnswer a = answered("2");
        canvas.setTool(DrawingCanvas.Tool.ERASER);
        int x = (int) a.getBox().getCenterX();
        drag(x, (int) a.getY() - 20, x, (int) (a.getY() + a.getHeight()) + 20);
        assertTrue(canvas.getAnswers().isEmpty());
    }

    @Test
    void boxSelectsAndDragsJustTheAnswer() {
        CanvasAnswer a = answered("2");
        canvas.setTool(DrawingCanvas.Tool.RECTANGLE);
        Rectangle2D box = a.getBox();
        drag((int) box.getX() - 5, (int) box.getY() - 5, (int) box.getMaxX() + 5, (int) box.getMaxY() + 5);
        assertTrue(canvas.hasSelection());
        int cx = (int) box.getCenterX();
        int cy = (int) box.getCenterY();
        drag(cx, cy, cx + 100, cy + 50);
        CanvasAnswer moved = canvas.getAnswers().get(0);
        assertEquals(a.getX() + 100, moved.getX(), 1);
        assertEquals(a.getY() + 50, moved.getY(), 1);
        assertEquals(1, canvas.getFormulas().size(), "formula still bound");
    }

    @Test
    void resizedAnswerKeepsPenWidthAndATightBox() {
        CanvasAnswer a = answered("2");
        canvas.setTool(DrawingCanvas.Tool.RECTANGLE);
        Rectangle2D box = a.getBox();
        drag((int) box.getX() - 5, (int) box.getY() - 5, (int) box.getMaxX() + 5, (int) box.getMaxY() + 5);
        // grab the bottom-right handle, pull it out
        Rectangle2D sel = canvas.worldToScreen(box);
        int hx = (int) Math.round(sel.getMaxX()) + 8;
        int hy = (int) Math.round(sel.getMaxY()) + 8;
        drag(hx, hy, hx + 60, hy + 60);
        CanvasAnswer big = canvas.getAnswers().get(0);
        assertTrue(big.getHeight() > a.getHeight() * 1.5, big.getHeight() + " vs " + a.getHeight());
        Rectangle2D ink = AnswerInk.outline(big, 4).getBounds2D();
        assertEquals(ink.getWidth(), big.getWidth(), 0.5);
        assertEquals(ink.getHeight(), big.getHeight(), 0.5);
    }
}
