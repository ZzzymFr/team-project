package view.canvas;

import view.glass.Glass;
import view.glass.GlassDock;
import view.glass.GlassLabel;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * A page of the app: the shared handwriting canvas filling the window, with the page's own controls
 * floating over it.
 *
 * <p>Subclasses such as {@link AnswerCanvasPage} put their controls in the function area with
 * {@link #setFunctionArea}. They write on the {@link SharedCanvas}. To work with what was written, a subclass
 * calls {@link #captureFormula()} (the image) or {@link #recognizeFormula()} (the LaTeX).
 */
public abstract class CanvasPage extends JPanel {
    private static final int FLOAT_INSET = 10;  // gap between the floating glass and the canvas edges
    private static final int FUNCTION_TOP = 18;
    private static final int GLASS_BOTTOM_SLACK = 58;  // from the function row's top to clear of it and the message
    private static final Color ERROR_TEXT = new Color(185, 28, 28);

    private final SharedCanvas sharedCanvas;
    private final GlassDock functionDock = new GlassDock();
    private final JPanel canvasSlot = new CanvasSlot();
    private final GlassLabel message = new GlassLabel("");
    private final GlassDock messageDock = new GlassDock();
    private final Timer messageTimer = new Timer(0, e -> hideMessage());
    private boolean messageIsError;
    private CanvasArea canvasArea;
    private int functionTop = FUNCTION_TOP;

    protected CanvasPage(SharedCanvas sharedCanvas) {
        super(new BorderLayout());
        this.sharedCanvas = sharedCanvas;
        setBackground(Glass.CANVAS);

        // The canvas fills the page; the controls float over it, showing the ink underneath through the glass.
        messageDock.add(message);
        messageDock.setVisible(false);
        messageTimer.setRepeats(false);
        functionDock.setBackdrop(sharedCanvas);
        messageDock.setBackdrop(sharedCanvas);
        canvasArea = new CanvasArea();
        add(canvasArea, BorderLayout.CENTER);
    }

    /**
     * Shows a short message floating at the top of the canvas, in red for errors.
     * Call on the Swing event thread.
     *
     * @param hideAfterMillis hide it after this long; 0 keeps it until the next message or hideMessage()
     */
    protected final void showMessage(String text, boolean error, int hideAfterMillis) {
        messageTimer.stop();
        message.setText(text);
        message.setForeground(error ? ERROR_TEXT : Glass.TEXT_MUTED);
        messageIsError = error;
        messageDock.setVisible(true);
        messageDock.revalidate();
        messageDock.repaint();
        if (hideAfterMillis > 0) {
            messageTimer.setInitialDelay(hideAfterMillis);
            messageTimer.start();
        }
    }

    protected final void hideMessage() {
        messageTimer.stop();
        messageDock.setVisible(false);
    }

    /** The message on show, or null if there is none. */
    String currentMessage() {
        return messageDock.isVisible() ? message.getText() : null;
    }

    boolean isMessageError() {
        return messageDock.isVisible() && messageIsError;
    }

    /**
     * Moves the function row (and the message under it) down to {@code top}, e.g. below a bar across
     * the top of the window, and tells the canvas to keep new answers and cards below them.
     */
    void setFunctionTop(int top) {
        functionTop = top;
        sharedCanvas.getDrawingCanvas().setViewMarginTop(top + GLASS_BOTTOM_SLACK);
        canvasArea.revalidate();
        canvasArea.repaint();
    }

    /** Floats this page's own glass controls at the top of the canvas, in a row. */
    protected final void setFunctionArea(JComponent... controls) {
        functionDock.removeAll();
        for (JComponent control : controls) {
            functionDock.add(control);
        }
        functionDock.revalidate();
        functionDock.repaint();
    }

    // floats over the canvas, on top; caller sets its bounds
    protected final void addCanvasOverlay(JComponent overlay) {
        canvasArea.add(overlay, 0);
        canvasArea.repaint();
    }

    // area the overlay lives in (same coords as the canvas)
    protected final JComponent getCanvasArea() {
        return canvasArea;
    }

    protected final SharedCanvas getSharedCanvas() {
        return sharedCanvas;
    }

    /** Screenshot of the formula on the canvas. @throws IllegalStateException if the canvas is empty */
    protected final BufferedImage captureFormula() {
        return sharedCanvas.captureFormulaImage();
    }

    /**
     * LaTeX of the formula on the canvas, from handwriting recognition. Call off the Swing event thread.
     *
     * @throws IllegalStateException if the canvas is empty or recognition is not connected yet
     */
    protected final String recognizeFormula() throws Exception {
        return sharedCanvas.recognizeFormula();
    }

    /**
     * LaTeX of a formula image already taken with {@link #captureFormula()}. Call off the Swing event thread.
     *
     * @throws IllegalStateException if recognition is not connected yet
     */
    protected final String recognizeFormula(BufferedImage formulaImage) throws Exception {
        return sharedCanvas.recognizeFormula(formulaImage);
    }

    /** A line of text saying what will go in the function area; for pages not built yet. */
    protected static JComponent placeholder(String text) {
        return new GlassLabel(text);
    }

    JPanel getCanvasSlot() {
        return canvasSlot;
    }

    /** The canvas filling the area, with the function row and message top centre. */
    private final class CanvasArea extends JPanel {
        CanvasArea() {
            super(null);
            setBackground(Glass.CANVAS);
            add(messageDock);  // added first = drawn last = on top
            add(functionDock);
            add(canvasSlot);
        }

        /** The docks overlap the canvas, so Swing must repaint them whenever the canvas repaints. */
        @Override
        public boolean isOptimizedDrawingEnabled() {
            return false;
        }

        @Override
        public void doLayout() {
            int w = getWidth();
            int h = getHeight();
            canvasSlot.setBounds(0, 0, w, h);
            Dimension functionSize = functionDock.getPreferredSize();
            int functionWidth = Math.min(functionSize.width, w);
            functionDock.setBounds((w - functionWidth) / 2, functionTop, functionWidth, functionSize.height);
            // the message sits below the function row (long text is cut off at the edges of the window)
            Dimension messageSize = messageDock.getPreferredSize();
            int messageWidth = Math.max(0, Math.min(messageSize.width, w - 2 * FLOAT_INSET));
            messageDock.setBounds((w - messageWidth) / 2, functionTop + functionSize.height,
                    messageWidth, messageSize.height);
        }
    }

    /** Holds the shared canvas while this page is in front; otherwise shows a copy of its ink. */
    private final class CanvasSlot extends JPanel {
        CanvasSlot() {
            super(new BorderLayout());
            setBackground(Glass.CANVAS);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getComponentCount() == 0) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.clipRect(0, 0, getWidth(), getHeight());
                sharedCanvas.paintCanvasCopy(g2);
                g2.dispose();
            }
        }
    }
}
