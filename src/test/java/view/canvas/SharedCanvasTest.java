package view.canvas;

import org.junit.jupiter.api.Test;
import view.DrawingCanvas;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedCanvasTest {
    private final DrawingCanvas canvas = new DrawingCanvas();
    private final SharedCanvas shared = new SharedCanvas(canvas);

    /** Draws a straight stroke the way the mouse would. */
    private void drawLine(int x1, int y1, int x2, int y2) {
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, 0, x1, y1, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0, 0, x2, y2, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 0, 0, x2, y2, 1, false, MouseEvent.BUTTON1));
    }

    private static boolean isWhite(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) & 0xFFFFFF) == 0xFFFFFF;
    }

    @Test
    void formulaImageIsCroppedToTheInkWithAWhiteMargin() {
        drawLine(100, 50, 140, 80);

        BufferedImage image = shared.captureFormulaImage();

        // 41 x 31 pixels of ink plus 16 px margin and half the 4 px pen width on each side
        assertEquals(41 + 2 * 18, image.getWidth());
        assertEquals(31 + 2 * 18, image.getHeight());
        assertTrue(isWhite(image, 0, 0));
        assertTrue(isWhite(image, image.getWidth() - 1, image.getHeight() - 1));
        assertEquals(Color.BLACK.getRGB(), image.getRGB(18 + 20, 18 + 15), "middle of the line is ink");
    }

    @Test
    void captureWorksOffTheEventThread() throws Exception {
        drawLine(10, 10, 20, 20);
        AtomicReference<BufferedImage> image = new AtomicReference<>();
        Thread worker = new Thread(() -> image.set(shared.captureFormulaImage()));
        worker.start();
        worker.join(5000);
        assertEquals(11 + 2 * 18, image.get().getWidth());
    }

    @Test
    void emptyCanvasCannotBeCaptured() {
        assertThrows(IllegalStateException.class, shared::captureFormulaImage);
    }

    @Test
    void recognitionSendsTheCapturedImageToTheRecognizer() throws Exception {
        drawLine(0, 0, 30, 0);
        AtomicReference<BufferedImage> received = new AtomicReference<>();
        shared.setFormulaRecognizer(image -> {
            received.set(image);
            return "y=x";
        });

        assertEquals("y=x", shared.recognizeFormula());
        assertEquals(31 + 2 * 18, received.get().getWidth());
    }

    @Test
    void recognitionWithoutRecognizerSaysItIsNotConnected() {
        drawLine(0, 0, 30, 0);
        assertFalse(shared.isRecognizerConnected());
        IllegalStateException e = assertThrows(IllegalStateException.class, shared::recognizeFormula);
        assertTrue(e.getMessage().contains("not connected"));
    }

    @Test
    void canvasMovesBetweenPagesAndKeepsItsInk() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            CanvasPage graph = new GraphCanvasPage(shared);
            CanvasPage pdf = new PdfCanvasPage(shared);
            CanvasPage answer = new AnswerCanvasPage(shared);

            shared.attachTo(graph);
            assertSame(graph.getCanvasSlot(), canvas.getParent());
            drawLine(5, 5, 50, 50);

            shared.attachTo(answer);
            assertSame(answer.getCanvasSlot(), canvas.getParent());
            assertEquals(0, graph.getCanvasSlot().getComponentCount(), "only one page holds the canvas");
            assertEquals(1, canvas.getStrokes().size(), "ink survives switching pages");

            shared.attachTo(pdf);
            shared.attachTo(pdf);  // attaching twice is harmless
            assertSame(pdf.getCanvasSlot(), canvas.getParent());
            assertEquals(0, answer.getCanvasSlot().getComponentCount());
        });
    }
}
