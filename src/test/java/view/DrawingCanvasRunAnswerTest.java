package view;

import entity.AnswerKind;
import entity.CanvasAnswer;
import entity.symbolic.TextRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// answers w/ raised exponent runs, e.g. 3x²
class DrawingCanvasRunAnswerTest {
    private static final List<TextRun> THREE_X_SQUARED = List.of(new TextRun("3x", false), new TextRun("2", true));

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

    // "1=" 40 tall, x 100..170
    private void writeOneEquals() {
        drag(100, 100, 100, 140);
        drag(130, 115, 170, 115);
        drag(130, 125, 170, 125);
    }

    private CanvasAnswer show(List<TextRun> runs) {
        FormulaCapture c = canvas.takeFormulasToCalculate().get(0);
        assertTrue(canvas.showAnswer(c.getId(), c.getRevision(), AnswerKind.AFTER_EQUALS, runs, false));
        return canvas.getAnswers().get(0);
    }

    private CanvasAnswer showPlain(String text) {
        FormulaCapture c = canvas.takeFormulasToCalculate().get(0);
        assertTrue(canvas.showAnswer(c.getId(), c.getRevision(), AnswerKind.AFTER_EQUALS, text, false));
        return canvas.getAnswers().get(0);
    }

    @Test
    void runAnswerIsWiderThanPlainDigits() {
        writeOneEquals();
        CanvasAnswer runs = show(THREE_X_SQUARED);
        assertEquals("3x2", runs.getText());
        assertEquals(THREE_X_SQUARED, runs.getRuns());
        Rectangle2D plain = AnswerFont.visualBounds("3x", runs.getFontSize());
        assertTrue(runs.getWidth() > plain.getWidth(), runs.getWidth() + " vs " + plain.getWidth());
        assertTrue(runs.getHeight() >= plain.getHeight());
        assertTrue(runs.getX() > 170);
    }

    @Test
    void plainAnswerHasNoRuns() {
        writeOneEquals();
        assertNull(showPlain("2").getRuns());
    }

    @Test
    void raisedRunSitsHigherAndSmaller() {
        float size = 100f;
        Rectangle2D base = AnswerFont.visualBounds(List.of(new TextRun("2", false)), size);
        Rectangle2D up = AnswerFont.visualBounds(List.of(new TextRun("2", true)), size);
        assertTrue(up.getHeight() < base.getHeight() * 0.8, up + " vs " + base);
        assertTrue(up.getMaxY() < base.getMaxY() - base.getHeight() * 0.3, "lifted off the baseline");
    }

    @Test
    void runAnswerNeverInFormulaImage() {
        writeOneEquals();
        BufferedImage before = canvas.toFormulaImage();
        show(THREE_X_SQUARED);
        BufferedImage after = canvas.toFormulaImage();
        assertEquals(before.getWidth(), after.getWidth());
        assertEquals(before.getHeight(), after.getHeight());
    }

    @Test
    void runAnswerDrawsInk() {
        writeOneEquals();
        CanvasAnswer a = show(THREE_X_SQUARED);
        BufferedImage img = new BufferedImage(900, 500, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        canvas.paint(g);
        g.dispose();
        int dark = 0;
        for (int x = (int) a.getX(); x < a.getX() + a.getWidth(); x++) {
            for (int y = (int) a.getY(); y < a.getY() + a.getHeight(); y++) {
                if ((img.getRGB(x, y) & 0xff) < 128) {
                    dark++;
                }
            }
        }
        assertTrue(dark > 20, "dark px " + dark);
    }

    @Test
    void runAnswerSurvivesMoveAndScale() {
        writeOneEquals();
        CanvasAnswer a = show(THREE_X_SQUARED);
        Rectangle2D box = a.getBox();
        canvas.setTool(DrawingCanvas.Tool.LASSO);
        int l = 176;
        int t = (int) box.getMinY() - 10;
        int r = (int) box.getMaxX() + 10;
        int b = (int) box.getMaxY() + 10;
        drag(l, t, r, t, r, b, l, b);
        assertTrue(canvas.hasSelection());
        int cx = (int) box.getCenterX();
        int cy = (int) box.getCenterY();
        drag(cx, cy, cx + 20, cy);
        CanvasAnswer moved = canvas.getAnswers().get(0);
        assertEquals(a.getX() + 20, moved.getX(), 0.01);
        assertEquals(THREE_X_SQUARED, moved.getRuns());
        CanvasAnswer bigger = moved.scaled(0, 0, 2);
        assertEquals(THREE_X_SQUARED, bigger.getRuns());
        assertEquals(moved.getWidth() * 2, bigger.getWidth(), 0.01);
    }
}
