package view.canvas;

import entity.AnswerKind;
import entity.CanvasAnswer;
import entity.Stroke;
import entity.symbolic.TextRun;
import interface_adapter.recognize.FormulaRecognizer;
import view.DrawingCanvas;
import view.FormulaCapture;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.HierarchyEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.LongConsumer;

/**
 * The one handwriting canvas shared by every {@link CanvasPage}.
 *
 * <p>There is a single {@link DrawingCanvas}; it sits in whichever page is in front and moves when
 * the user switches pages, so the ink never disappears. Pages that are not in front paint a copy
 * of the ink, so they look the same in the page-switch animation.
 *
 * <p>{@link #captureFormulaImage()} takes the screenshot of the formula, and
 * {@link #recognizeFormula()} sends it to handwriting recognition once a
 * {@link FormulaRecognizer} is connected. {@link #showGraph} puts a graph beside the formula.
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

    /** The ink and graph card as they appear on screen, e.g. for the frosted backdrop behind floating glass. */
    void paintInkCopy(Graphics2D g2) {
        canvas.paintContent(g2);
    }

    /** Shows the graph as a card beside the formula (not over the whole canvas). Call on the Swing event thread. */
    public void showGraph(BufferedImage graph) {
        canvas.showGraph(graph);
    }

    /** Shows the graph as a card beside the formula found at {@code formula} (from {@link #getFormulaBounds()}). */
    public void showGraph(BufferedImage graph, Rectangle formula) {
        canvas.showGraph(graph, formula);
    }

    /** Where the formula is: the lasso selection if there is one, otherwise all the ink; null if blank. */
    public Rectangle getFormulaBounds() {
        return canvas.getFormulaBounds();
    }

    public void removeGraph() {
        canvas.removeGraph();
    }

    public boolean hasGraph() {
        return canvas.hasGraph();
    }

    /** The grid and ink as they appear on screen, for pages that are not in front. */
    void paintCanvasCopy(Graphics2D g2) {
        canvas.paintView(g2);
    }

    /** The zoom of the infinite canvas: 1 = 100%. */
    public double getZoom() {
        return canvas.getZoom();
    }

    /** Back to 100%, with the ink in the middle. */
    public void resetView() {
        canvas.resetView();
    }

    /** Called with the new zoom whenever it changes. */
    public void addZoomListener(DoubleConsumer listener) {
        canvas.addPropertyChangeListener(DrawingCanvas.ZOOM_PROPERTY,
                e -> listener.accept((Double) e.getNewValue()));
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

    // answers on the canvas, all EDT
    public List<FormulaCapture> takeFormulasToCalculate() {
        return canvas.takeFormulasToCalculate();
    }

    public FormulaCapture captureForRecalculation(long id) {
        return canvas.captureForRecalculation(id);
    }

    public boolean isCurrent(long id, long revision) {
        return canvas.isCurrent(id, revision);
    }

    public boolean showAnswer(long id, long revision, AnswerKind kind, String text, boolean error) {
        return canvas.showAnswer(id, revision, kind, text, error);
    }

    public boolean showAnswer(long id, long revision, AnswerKind kind, List<TextRun> runs, boolean error) {
        return canvas.showAnswer(id, revision, kind, runs, error);
    }

    public void addFormulaChangeListener(LongConsumer listener) {
        canvas.addFormulaChangeListener(listener);
    }

    public List<CanvasAnswer> getAnswers() {
        return canvas.getAnswers();
    }

    // hooks for the answer popup, all EDT
    public void addStrokeListener(Consumer<Stroke> listener) {
        canvas.addStrokeListener(listener);
    }

    public void addUserActionListener(Runnable listener) {
        canvas.addUserActionListener(listener);
    }

    public void addViewListener(Runnable listener) {
        canvas.addViewListener(listener);
    }

    // canvas moved to another page (or out of one)
    public void addPageSwitchListener(Runnable listener) {
        canvas.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0 && e.getChanged() == canvas) {
                listener.run();
            }
        });
    }

    public boolean isAttachedTo(CanvasPage page) {
        return canvas.getParent() == page.getCanvasSlot();
    }

    public Rectangle2D worldToScreen(Rectangle2D world) {
        return canvas.worldToScreen(world);
    }

    public FormulaCapture registerNewInk(Stroke last, Collection<Stroke> session) {
        return canvas.registerNewInk(last, session);
    }

    public Rectangle formulaBounds(long id) {
        return canvas.formulaBounds(id);
    }

    public boolean isBound(Stroke s) {
        return canvas.isBound(s);
    }

    public boolean isBusy() {
        return canvas.isBusy();
    }

    /**
     * Screenshot of the formula on the canvas (cropped, dark ink on white): the lasso selection if
     * there is one, otherwise all the ink. Safe to call from any thread.
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
        return recognizeFormula(captureFormulaImage());
    }

    /**
     * The LaTeX that handwriting recognition reads from an image already captured with
     * {@link #captureFormulaImage()}. Call off the Swing event thread: recognition may be slow.
     *
     * @throws IllegalStateException if no recognizer is connected yet
     * @throws Exception if recognition itself fails
     */
    public String recognizeFormula(BufferedImage formulaImage) throws Exception {
        if (recognizer == null) {
            throw new IllegalStateException("Handwriting recognition is not connected yet.");
        }
        return recognizer.recognize(formulaImage);
    }
}
