package view.canvas;

import view.DrawingCanvas;
import view.glass.Glass;
import view.glass.GlassButton;
import view.glass.GlassDock;
import view.glass.GlassLabel;
import view.glass.GlassSegmentedControl;
import view.stack.Fonts;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * A page of the app: its own function area at the top, and the shared handwriting canvas below.
 *
 * <p>Subclasses ({@link GraphCanvasPage}, {@link PdfCanvasPage}, {@link AnswerCanvasPage}) put their
 * controls in the function area with {@link #setFunctionArea}. They all write on the same
 * {@link SharedCanvas}, so switching pages keeps the ink. To work with what was written, a subclass
 * calls {@link #captureFormula()} (the image) or {@link #recognizeFormula()} (the LaTeX).
 */
public abstract class CanvasPage extends JPanel {
    private static final Color LINE = new Color(226, 232, 240);
    private static final Color HINT = new Color(100, 116, 139);
    private static final int FLOAT_INSET = 10;  // gap between the floating glass and the canvas edges
    private static final Color ERROR_TEXT = new Color(185, 28, 28);

    private final SharedCanvas sharedCanvas;
    private final JPanel functionArea = new JPanel(new BorderLayout());
    private final JPanel canvasSlot = new CanvasSlot();
    private final GlassLabel hint = new GlassLabel("");
    private final GlassDock hintDock = new GlassDock();
    private final GlassDock toolDock = new GlassDock();
    private final GlassDock zoomDock = new GlassDock();
    private final GlassLabel message = new GlassLabel("");
    private final GlassDock messageDock = new GlassDock();
    private final Timer messageTimer = new Timer(0, e -> hideMessage());
    private boolean messageIsError;

    protected CanvasPage(SharedCanvas sharedCanvas) {
        super(new BorderLayout());
        this.sharedCanvas = sharedCanvas;
        setBackground(Color.WHITE);

        functionArea.setBackground(Color.WHITE);
        functionArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, LINE),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)));
        add(functionArea, BorderLayout.NORTH);

        // The canvas fills the rest of the page; the hint and the tools float over its bottom edge
        // and the zoom over its top-right corner, showing a frosted copy of any ink underneath.
        hintDock.add(hint);
        buildTools();
        buildZoomBadge();
        messageDock.add(message);
        messageDock.setVisible(false);
        messageTimer.setRepeats(false);
        hintDock.setBackdrop(sharedCanvas::paintInkCopy);
        toolDock.setBackdrop(sharedCanvas::paintInkCopy);
        zoomDock.setBackdrop(sharedCanvas::paintInkCopy);
        messageDock.setBackdrop(sharedCanvas::paintInkCopy);
        add(new CanvasArea(), BorderLayout.CENTER);
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

    /** Shows the zoom of the infinite canvas; click it to go back to 100% with the ink in the middle. */
    private void buildZoomBadge() {
        GlassButton zoom = new GlassButton(percent(sharedCanvas.getZoom()));
        zoom.setToolTipText("Ctrl+scroll to zoom. Click to go back to 100% with the ink in the middle.");
        zoom.addActionListener(e -> sharedCanvas.resetView());
        sharedCanvas.addZoomListener(value -> {
            zoom.setText(percent(value));
            zoomDock.revalidate();
        });
        zoomDock.add(zoom);
    }

    private static String percent(double zoom) {
        return Math.round(zoom * 100) + "%";
    }

    /** Puts this page's own controls at the top of the page. */
    protected final void setFunctionArea(JComponent component) {
        functionArea.removeAll();
        clearPanelBackgrounds(component);  // panels inside blend into the white page
        functionArea.add(component, BorderLayout.CENTER);
        functionArea.revalidate();
        functionArea.repaint();
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

    /** A grey line of text saying what will go in the function area; for pages not built yet. */
    protected static JComponent placeholder(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Fonts.ui(Font.ITALIC, 13f));
        label.setForeground(HINT);
        label.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
        return label;
    }

    private static void clearPanelBackgrounds(Component component) {
        if (component instanceof JPanel) {
            ((JPanel) component).setOpaque(false);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                clearPanelBackgrounds(child);
            }
        }
    }

    JPanel getCanvasSlot() {
        return canvasSlot;
    }

    private void buildTools() {
        hint.setText(hintFor(sharedCanvas.getTool(), sharedCanvas.getEraserMode()));

        // The tool and eraser mode belong to the shared canvas, so every page shows the same ones.
        GlassSegmentedControl tools = new GlassSegmentedControl("Pen", "Eraser", "Lasso");
        tools.setSelectedIndex(sharedCanvas.getTool().ordinal());
        tools.addSelectionListener(i -> sharedCanvas.setTool(DrawingCanvas.Tool.values()[i]));

        GlassSegmentedControl eraserModes = new GlassSegmentedControl("Precise", "Stroke");
        eraserModes.setSelectedIndex(sharedCanvas.getEraserMode().ordinal());
        eraserModes.setToolTipText("Precise erases only the ink under the eraser; Stroke erases whole strokes.");
        eraserModes.setVisible(sharedCanvas.getTool() == DrawingCanvas.Tool.ERASER);
        eraserModes.addSelectionListener(i -> sharedCanvas.setEraserMode(DrawingCanvas.EraserMode.values()[i]));

        sharedCanvas.addToolListener(tool -> {
            tools.setSelectedIndex(tool.ordinal());
            eraserModes.setVisible(tool == DrawingCanvas.Tool.ERASER);
            hint.setText(hintFor(tool, sharedCanvas.getEraserMode()));
            toolDock.revalidate();
        });
        sharedCanvas.addEraserModeListener(mode -> {
            eraserModes.setSelectedIndex(mode.ordinal());
            hint.setText(hintFor(sharedCanvas.getTool(), mode));
        });

        GlassButton undo = new GlassButton("Undo");
        undo.addActionListener(e -> sharedCanvas.undo());
        GlassButton clear = new GlassButton("Clear");
        clear.addActionListener(e -> sharedCanvas.clear());

        toolDock.add(eraserModes);
        toolDock.add(tools);
        toolDock.add(Box.createHorizontalStrut(6));
        toolDock.add(undo);
        toolDock.add(clear);
    }

    private static String hintFor(DrawingCanvas.Tool tool, DrawingCanvas.EraserMode eraserMode) {
        switch (tool) {
            case PEN:
                return "Write anywhere · scroll or right-drag to move · Ctrl+scroll to zoom";
            case ERASER:
                return eraserMode == DrawingCanvas.EraserMode.PRECISE
                        ? "Drag over the ink to erase just that part."
                        : "Touch a stroke to erase all of it.";
            default:
                return "Circle ink to select · drag to move · drag a corner to resize · Delete to remove";
        }
    }

    /** The canvas filling the area, with the hint floating bottom-left and the tools bottom-right. */
    private final class CanvasArea extends JPanel {
        CanvasArea() {
            super(null);
            setBackground(Color.WHITE);
            add(messageDock);  // added first = drawn last = on top
            add(toolDock);
            add(hintDock);
            add(zoomDock);
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
            Dimension tools = toolDock.getPreferredSize();
            toolDock.setBounds(w - FLOAT_INSET - tools.width, h - FLOAT_INSET - tools.height,
                    tools.width, tools.height);
            Dimension hintSize = hintDock.getPreferredSize();
            hintDock.setVisible(3 * FLOAT_INSET + hintSize.width + tools.width <= w);  // hide it if there is no room
            hintDock.setBounds(FLOAT_INSET, h - FLOAT_INSET - hintSize.height, hintSize.width, hintSize.height);
            Dimension zoomSize = zoomDock.getPreferredSize();
            zoomDock.setBounds(w - FLOAT_INSET - zoomSize.width, FLOAT_INSET, zoomSize.width, zoomSize.height);
            // the message sits top centre, kept clear of the zoom badge (long text is shortened with …)
            Dimension messageSize = messageDock.getPreferredSize();
            int messageWidth = Math.max(0, Math.min(messageSize.width, w - 2 * (zoomSize.width + 2 * FLOAT_INSET)));
            messageDock.setBounds((w - messageWidth) / 2, FLOAT_INSET, messageWidth, messageSize.height);
        }
    }

    /** Holds the shared canvas while this page is in front; otherwise shows a copy of its ink. */
    private final class CanvasSlot extends JPanel {
        CanvasSlot() {
            super(new BorderLayout());
            setBackground(Color.WHITE);
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
