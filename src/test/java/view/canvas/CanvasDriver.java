package view.canvas;

import view.DrawingCanvas;

import javax.swing.SwingUtilities;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Writes and circles on a canvas as the user would, and waits for background work, for the page tests. */
final class CanvasDriver {
    private final DrawingCanvas canvas;

    CanvasDriver(DrawingCanvas canvas) {
        this.canvas = canvas;
    }

    /** A pen stroke through the points (x, y, x, y, ...). */
    void write(int... xy) throws Exception {
        onEdt(() -> canvas.setTool(DrawingCanvas.Tool.PEN));
        drag(xy);
    }

    /** Drags with whatever tool is on. */
    void drag(int... xy) throws Exception {
        onEdt(() -> {
            send(MouseEvent.MOUSE_PRESSED, xy[0], xy[1]);
            for (int i = 2; i < xy.length; i += 2) {
                send(MouseEvent.MOUSE_DRAGGED, xy[i], xy[i + 1]);
            }
            send(MouseEvent.MOUSE_RELEASED, xy[xy.length - 2], xy[xy.length - 1]);
        });
    }

    /** Circles the rectangle with the lasso. */
    void lasso(int left, int top, int right, int bottom) throws Exception {
        onEdt(() -> canvas.setTool(DrawingCanvas.Tool.LASSO));
        drag(left, top, right, top, right, bottom, left, bottom);
    }

    private void send(int id, int x, int y) {
        canvas.dispatchEvent(new MouseEvent(canvas, id, 0, 0, x, y, 1, false, MouseEvent.BUTTON1));
    }

    static void onEdt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    /** Waits up to 5 s for background work to make the condition true, checking on the event thread. */
    static void waitFor(BooleanSupplier condition, String what) throws Exception {
        AtomicBoolean met = new AtomicBoolean();
        for (int i = 0; i < 250 && !met.get(); i++) {
            Thread.sleep(20);
            onEdt(() -> met.set(condition.getAsBoolean()));
        }
        assertTrue(met.get(), "timed out waiting for " + what);
    }

    /** Whether the condition holds, checked on the event thread. */
    static boolean check(BooleanSupplier condition) throws Exception {
        AtomicBoolean result = new AtomicBoolean();
        onEdt(() -> result.set(condition.getAsBoolean()));
        return result.get();
    }

    /** Waits for the page to have read the circled ink as {@code latex}. */
    static void waitForReading(AnswerCanvasPage page, String latex) throws Exception {
        waitFor(() -> latex.equals(page.getSelectedLatex()), "the circled formula read as " + latex);
    }
}
