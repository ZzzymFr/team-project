package view;

import entity.InkEditor;
import entity.Stroke;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrawingCanvasInkColorTest {
    private static final Color RED = new Color(255, 59, 48);
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

    private BufferedImage shown() {
        BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        canvas.setSize(200, 100);
        canvas.paint(g);
        g.dispose();
        return image;
    }

    @Test
    void strokesKeepTheColourTheyWereWrittenIn() {
        canvas.setInkColor(RED);
        drag(10, 10, 50, 10);
        canvas.setInkColor(Color.BLUE);
        drag(10, 30, 50, 30);

        assertEquals(RED.getRGB() & 0xFFFFFF, canvas.getStrokes().get(0).getColor());
        assertEquals(Color.BLUE.getRGB() & 0xFFFFFF, canvas.getStrokes().get(1).getColor());
    }

    @Test
    void theCanvasShowsTheInkInItsColour() {
        canvas.setInkColor(RED);
        drag(10, 50, 100, 50);
        assertEquals(RED.getRGB(), shown().getRGB(50, 50));
    }

    @Test
    void recognitionAlwaysSeesBlackInk() {
        canvas.setInkColor(RED);
        drag(10, 50, 100, 50);
        BufferedImage formula = canvas.toFormulaImage();
        boolean sawBlack = false;
        for (int x = 0; x < formula.getWidth(); x++) {
            for (int y = 0; y < formula.getHeight(); y++) {
                int rgb = formula.getRGB(x, y) & 0xFFFFFF;
                int r = rgb >> 16;
                int b = rgb & 0xFF;
                assertEquals(r, b, "grey only: no colour reaches the recogniser");
                sawBlack |= rgb == 0;
            }
        }
        assertTrue(sawBlack);
    }

    @Test
    void copiesOfAStrokeKeepItsColour() {
        Stroke red = new Stroke(0xFF3B30);
        red.addPoint(new entity.InkPoint(0, 0, 0));
        red.addPoint(new entity.InkPoint(100, 0, 1));

        assertEquals(0xFF3B30, InkEditor.translate(red, 5, 5).getColor());
        assertEquals(0xFF3B30, InkEditor.scale(red, 0, 0, 2).getColor());
    }

    @Test
    void erasingTheMiddleOfAStrokeLeavesPiecesInTheSameColour() {
        canvas.setInkColor(RED);
        drag(10, 50, 190, 50);
        canvas.setTool(DrawingCanvas.Tool.ERASER);
        drag(100, 40, 100, 60);

        List<Stroke> pieces = canvas.getStrokes();
        assertEquals(2, pieces.size());
        for (Stroke piece : pieces) {
            assertEquals(RED.getRGB() & 0xFFFFFF, piece.getColor());
        }
    }

    @Test
    void movingTheEraserOutlineLeavesTheBackdropVersionAlone() {
        canvas.setSize(200, 100);
        canvas.setTool(DrawingCanvas.Tool.ERASER);
        long before = canvas.getBackdropVersion();
        send(MouseEvent.MOUSE_MOVED, 50, 50);
        assertEquals(before, canvas.getBackdropVersion());

        drag(10, 50, 100, 50);
        assertNotEquals(before, canvas.getBackdropVersion());
    }

    @Test
    void undoIsAvailableOnlyAfterAChange() {
        assertFalse(canvas.canUndo());
        drag(10, 50, 100, 50);
        assertTrue(canvas.canUndo());
        canvas.undo();
        assertFalse(canvas.canUndo());
    }
}
