package view.canvas;

import interface_adapter.recognize.FormulaRecognizer;
import view.DrawingCanvas;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Consumer;

/**
 * The one handwriting canvas shared by every {@link CanvasPage}.
 *
 * <p>There is a single {@link DrawingCanvas}; it sits in whichever page is in front and moves when
 * the user switches pages, so the ink never disappears. Pages that are not in front paint a copy
 * of the ink, so they look the same in the page-switch animation.
 *
 * <p>{@link #captureFormulaImage()} takes the screenshot of the formula, and
 * {@link #recognizeFormula()} sends it to handwriting recognition once a
 * {@link FormulaRecognizer} is connected. {@code recognizeFormula} has the same shape as
 * {@code interface_adapter.graph.LatexProvider}, so {@code graphView.setLatexProvider(shared::recognizeFormula)} works.
 */
public class SharedCanvas {
    private final DrawingCanvas canvas;
    private FormulaRecognizer recognizer;

    public SharedCanvas(DrawingCanvas canvas) {
        this.canvas = canvas;
    }

    public DrawingCanvas getDrawingCanvas() {
        return canvas;
    }

    /** Connects handwriting recognition (the machine-learning part). */
    public void setFormulaRecognizer(FormulaRecognizer recognizer) {
        this.recognizer = recognizer;
    }

    public boolean isRecognizerConnected() {
        return recognizer != null;
    }

    /** Moves the canvas, with its ink, into the given page. Call on the Swing event thread. */
    public void attachTo(CanvasPage page) {
        JPanel slot = page.getCanvasSlot();
        Container previous = canvas.getParent();
        if (previous == slot) {
            return;
        }
        if (previous != null) {
            previous.remove(canvas);
            previous.revalidate();
            previous.repaint();
        }
        slot.add(canvas, BorderLayout.CENTER);
        slot.revalidate();
        slot.repaint();
    }

    void paintInkCopy(Graphics2D g2) {
        canvas.paintInk(g2);
    }

    public DrawingCanvas.Tool getTool() {
        return canvas.getTool();
    }

    /** Switches the tool for every page, since they all share this canvas. */
    public void setTool(DrawingCanvas.Tool tool) {
        canvas.setTool(tool);
    }

    /** Called with the new tool whenever it changes, from any page. */
    public void addToolListener(Consumer<DrawingCanvas.Tool> listener) {
        canvas.addPropertyChangeListener(DrawingCanvas.TOOL_PROPERTY,
                e -> listener.accept((DrawingCanvas.Tool) e.getNewValue()));
    }

    public DrawingCanvas.EraserMode getEraserMode() {
        return canvas.getEraserMode();
    }

    public void setEraserMode(DrawingCanvas.EraserMode mode) {
        canvas.setEraserMode(mode);
    }

    /** Called with the new eraser mode whenever it changes, from any page. */
    public void addEraserModeListener(Consumer<DrawingCanvas.EraserMode> listener) {
        canvas.addPropertyChangeListener(DrawingCanvas.ERASER_MODE_PROPERTY,
                e -> listener.accept((DrawingCanvas.EraserMode) e.getNewValue()));
    }

    public void undo() {
        canvas.undo();
    }

    public void clear() {
        canvas.clear();
    }

    public boolean isBlank() {
        return canvas.isBlank();
    }

    /**
     * Screenshot of the formula on the canvas (cropped, dark ink on white). Safe to call from any thread.
     *
     * @throws IllegalStateException if the canvas is empty
     */
    public BufferedImage captureFormulaImage() {
        if (SwingUtilities.isEventDispatchThread()) {
            return canvas.toFormulaImage();
        }
        BufferedImage[] image = new BufferedImage[1];
        RuntimeException[] failure = new RuntimeException[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    image[0] = canvas.toFormulaImage();
                } catch (RuntimeException e) {
                    failure[0] = e;
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while capturing the canvas.", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Could not capture the canvas.", e.getCause());
        }
        if (failure[0] != null) {
            throw failure[0];
        }
        return image[0];
    }

    /**
     * Captures the formula and returns the LaTeX that handwriting recognition reads from it.
     * Call off the Swing event thread: recognition may be slow.
     *
     * @throws IllegalStateException if the canvas is empty or no recognizer is connected yet
     * @throws Exception if recognition itself fails
     */
    public String recognizeFormula() throws Exception {
        if (recognizer == null) {
            throw new IllegalStateException("Handwriting recognition is not connected yet.");
        }
        return recognizer.recognize(captureFormulaImage());
    }
}
