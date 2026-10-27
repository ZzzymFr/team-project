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
    void wideFormulaImageIsCroppedToTheInkWithAWhiteMargin() {
        drawLine(100, 50, 300, 60);

        BufferedImage image = shared.captureFormulaImage();

        // 201 x 11 pixels of ink plus 16 px margin and half the 4 px pen width on each side
        assertEquals(201 + 2 * 18, image.getWidth());
        assertEquals(11 + 2 * 18, image.getHeight());
        assertTrue(isWhite(image, 0, 0));
        assertTrue(isWhite(image, image.getWidth() - 1, image.getHeight() - 1));
        assertEquals(Color.BLACK.getRGB(), image.getRGB(18 + 100, 18 + 5), "middle of the line is ink");
    }

    @Test
    void formulaImageIsAlwaysALandscapeRectangle() {
        drawLine(50, 0, 50, 300);   // one tall, narrow stroke

        BufferedImage image = shared.captureFormulaImage();

        assertEquals(301 + 2 * 18, image.getHeight());
        assertTrue(image.getWidth() >= 2 * image.getHeight(), "at least twice as wide as tall");
        int middle = image.getWidth() / 2;
        assertEquals(Color.BLACK.getRGB(), image.getRGB(middle, image.getHeight() / 2), "the ink is centred");
        assertTrue(isWhite(image, 0, image.getHeight() / 2));
        assertTrue(isWhite(image, image.getWidth() - 1, image.getHeight() / 2));
    }

    @Test
    void squareInkIsWidenedToo() {
        drawLine(100, 50, 140, 80);   // 41 x 31 of ink: taller than half its width with the margin
        BufferedImage image = shared.captureFormulaImage();
        assertEquals(31 + 2 * 18, image.getHeight());
        assertEquals(2 * (31 + 2 * 18), image.getWidth());
    }

    /** Draws a lasso loop around the rectangle (left, top)-(right, bottom). */
    private void lassoAround(int left, int top, int right, int bottom) {
        canvas.setTool(DrawingCanvas.Tool.LASSO);
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, 0, left, top, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0, 0, right, top, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0, 0, right, bottom, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_DRAGGED, 0, 0, left, bottom, 1, false, MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_RELEASED, 0, 0, left, bottom, 1, false, MouseEvent.BUTTON1));
    }

    @Test
    void withALassoSelectionOnlyThatFormulaIsCaptured() {
        drawLine(0, 0, 30, 0);        // formula A
        drawLine(300, 300, 400, 300); // formula B
        assertEquals(674, shared.captureFormulaImage().getWidth(), "no selection: both formulas");

        lassoAround(-20, -20, 60, 20);

        BufferedImage image = shared.captureFormulaImage();
        assertEquals(1 + 2 * 18, image.getHeight(), "only A's height");
        assertEquals(2 * (1 + 2 * 18), image.getWidth(), "only A's width (made landscape)");
        assertEquals(new java.awt.Rectangle(0, 0, 30, 0), shared.getFormulaBounds());
    }

    @Test
    void withoutASelectionTheFormulaIsAllTheInk() {
        drawLine(0, 0, 30, 0);
        drawLine(300, 300, 400, 300);
        assertEquals(new java.awt.Rectangle(0, 0, 400, 300), shared.getFormulaBounds());
    }

    @Test
    void captureWorksOffTheEventThread() throws Exception {
        drawLine(10, 10, 20, 20);
        AtomicReference<BufferedImage> image = new AtomicReference<>();
        Thread worker = new Thread(() -> image.set(shared.captureFormulaImage()));
        worker.start();
        worker.join(5000);
        assertEquals(2 * (11 + 2 * 18), image.get().getWidth());
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
        assertEquals(2 * (1 + 2 * 18), received.get().getWidth());
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
