package view;

import entity.InkEditor;
import entity.InkPoint;
import entity.Stroke;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A white panel the user writes on with the mouse or a pen; keeps the ink as strokes.
 *
 * <p>Three tools: the pen writes, the eraser removes ink, and the lasso selects the strokes it is
 * drawn around. The eraser has two modes: precise rubs out only the ink under it (cutting strokes
 * where it passes), stroke removes every stroke it touches. Drag a lasso selection to move it,
 * or press Delete to remove it. Undo (also Ctrl+Z) steps back through every change, including Clear.
 */
public class DrawingCanvas extends JPanel {
    /** The drawing tools. */
    public enum Tool { PEN, ERASER, LASSO }

    /** How the eraser works: rub out just the ink under it, or remove each stroke it touches. */
    public enum EraserMode { PRECISE, STROKE }

    /** Property fired when the tool changes, so toolbars can show the current tool. */
    public static final String TOOL_PROPERTY = "tool";
    /** Property fired when the eraser mode changes. */
    public static final String ERASER_MODE_PROPERTY = "eraserMode";

    private static final float INK_WIDTH = 4f;
    private static final Color INK_COLOR = Color.BLACK;
    private static final int FORMULA_MARGIN = 16;  // white space kept around the ink in toFormulaImage()
    private static final int PRECISE_ERASER_RADIUS = 12;
    private static final int STROKE_ERASER_RADIUS = 6;  // only has to touch a stroke
    private static final Color STROKE_ERASER_OUTLINE = new Color(220, 38, 38);
    private static final int HISTORY_LIMIT = 100;
    private static final Color SELECTION = new Color(37, 99, 235);
    private static final Color SELECTION_HALO = new Color(37, 99, 235, 60);
    private static final Color ERASER_OUTLINE = new Color(100, 116, 139);
    private static final BasicStroke DASHED = new BasicStroke(1.2f, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_ROUND, 10f, new float[]{5f, 4f}, 0f);

    private final List<Stroke> strokes = new ArrayList<>();
    private final Deque<List<Stroke>> history = new ArrayDeque<>();
    private Tool tool = Tool.PEN;
    private EraserMode eraserMode = EraserMode.PRECISE;
    private Stroke currentStroke;

    // eraser
    private Point eraserAt;           // where the eraser outline is drawn; null when the mouse is away
    private Point lastErasePoint;     // previous drag position, to erase along the path
    private boolean savedBeforeErase; // one undo step per eraser drag

    // lasso
    private List<InkPoint> lassoPath; // the loop being drawn
    private final Set<Stroke> selection = new HashSet<>();
    private Point moveStart;          // where a drag of the selection started
    private int moveDx;
    private int moveDy;

    public DrawingCanvas() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(900, 500));
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();  // so Delete and Ctrl+Z reach the canvas
                switch (tool) {
                    case PEN:
                        startStroke(e);
                        break;
                    case ERASER:
                        savedBeforeErase = false;
                        lastErasePoint = e.getPoint();
                        eraseAlong(e.getPoint());
                        break;
                    default:
                        startLasso(e);
                        break;
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                switch (tool) {
                    case PEN:
                        if (currentStroke != null) {
                            record(e);
                        }
                        break;
                    case ERASER:
                        eraserAt = e.getPoint();
                        eraseAlong(e.getPoint());
                        break;
                    default:
                        dragLasso(e);
                        break;
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                currentStroke = null;
                lastErasePoint = null;
                if (tool == Tool.LASSO) {
                    finishLasso();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (tool == Tool.ERASER) {
                    eraserAt = e.getPoint();
                    repaint();
                } else if (tool == Tool.LASSO) {
                    boolean overSelection = selectionBounds() != null && selectionBounds().contains(e.getPoint());
                    setCursor(Cursor.getPredefinedCursor(overSelection ? Cursor.MOVE_CURSOR : Cursor.CROSSHAIR_CURSOR));
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (eraserAt != null) {
                    eraserAt = null;
                    repaint();
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        bindKey("DELETE", "deleteSelection", this::deleteSelection);
        bindKey("BACK_SPACE", "deleteSelection", this::deleteSelection);
        bindKey("ESCAPE", "clearSelection", this::clearSelection);
        bindKey("ctrl Z", "undo", this::undo);
    }

    private void bindKey(String key, String name, Runnable action) {
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    // ---------------------------------------------------------------- tools

    public Tool getTool() {
        return tool;
    }

    public void setTool(Tool tool) {
        Tool old = this.tool;
        if (old == tool) {
            return;
        }
        this.tool = tool;
        currentStroke = null;
        lassoPath = null;
        eraserAt = null;
        clearSelection();
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        repaint();
        firePropertyChange(TOOL_PROPERTY, old, tool);
    }

    public EraserMode getEraserMode() {
        return eraserMode;
    }

    public void setEraserMode(EraserMode eraserMode) {
        EraserMode old = this.eraserMode;
        if (old == eraserMode) {
            return;
        }
        this.eraserMode = eraserMode;
        repaint();
        firePropertyChange(ERASER_MODE_PROPERTY, old, eraserMode);
    }

    private int eraserRadius() {
        return eraserMode == EraserMode.PRECISE ? PRECISE_ERASER_RADIUS : STROKE_ERASER_RADIUS;
    }

    private void startStroke(MouseEvent e) {
        clearSelection();
        saveForUndo();
        currentStroke = new Stroke();
        strokes.add(currentStroke);
        record(e);
    }

    private void record(MouseEvent e) {
        currentStroke.addPoint(new InkPoint(e.getX(), e.getY(), e.getWhen()));
        repaint();
    }

    private void eraseAlong(Point to) {
        Point from = lastErasePoint == null ? to : lastErasePoint;
        List<Stroke> after = eraserMode == EraserMode.PRECISE
                ? InkEditor.eraseAlong(strokes, from.x, from.y, to.x, to.y, eraserRadius())
                : InkEditor.removeTouchedAlong(strokes, from.x, from.y, to.x, to.y, eraserRadius());
        if (after != strokes) {
            if (!savedBeforeErase) {
                saveForUndo();
                savedBeforeErase = true;
            }
            replaceStrokes(after);
        }
        lastErasePoint = to;
        repaint();
    }

    private void startLasso(MouseEvent e) {
        Rectangle bounds = selectionBounds();
        if (bounds != null && bounds.contains(e.getPoint())) {
            moveStart = e.getPoint();
            moveDx = 0;
            moveDy = 0;
            return;
        }
        clearSelection();
        lassoPath = new ArrayList<>();
        lassoPath.add(new InkPoint(e.getX(), e.getY(), e.getWhen()));
    }

    private void dragLasso(MouseEvent e) {
        if (moveStart != null) {
            moveDx = e.getX() - moveStart.x;
            moveDy = e.getY() - moveStart.y;
        } else if (lassoPath != null) {
            lassoPath.add(new InkPoint(e.getX(), e.getY(), e.getWhen()));
        }
        repaint();
    }

    private void finishLasso() {
        if (moveStart != null) {
            if (moveDx != 0 || moveDy != 0) {
                moveSelection(moveDx, moveDy);
            }
            moveStart = null;
            moveDx = 0;
            moveDy = 0;
        } else if (lassoPath != null) {
            selection.addAll(InkEditor.selectInside(strokes, lassoPath));
            lassoPath = null;
        }
        repaint();
    }

    private void moveSelection(int dx, int dy) {
        saveForUndo();
        List<Stroke> after = new ArrayList<>();
        Set<Stroke> moved = new HashSet<>();
        for (Stroke stroke : strokes) {
            if (selection.contains(stroke)) {
                Stroke shifted = InkEditor.translate(stroke, dx, dy);
                after.add(shifted);
                moved.add(shifted);
            } else {
                after.add(stroke);
            }
        }
        replaceStrokes(after);
        selection.clear();
        selection.addAll(moved);  // the moved strokes stay selected
    }

    public boolean hasSelection() {
        return !selection.isEmpty();
    }

    /** Removes the strokes selected with the lasso. */
    public void deleteSelection() {
        if (selection.isEmpty()) {
            return;
        }
        saveForUndo();
        List<Stroke> after = new ArrayList<>(strokes);
        after.removeAll(selection);
        replaceStrokes(after);
        selection.clear();
        repaint();
    }

    public void clearSelection() {
        if (!selection.isEmpty()) {
            selection.clear();
            repaint();
        }
    }

    /** The selection's outline in canvas coordinates (including a drag in progress), or null. */
    private Rectangle selectionBounds() {
        if (selection.isEmpty()) {
            return null;
        }
        Rectangle bounds = null;
        for (Stroke stroke : selection) {
            for (InkPoint p : stroke.getPoints()) {
                if (bounds == null) {
                    bounds = new Rectangle(p.getX(), p.getY(), 0, 0);
                } else {
                    bounds.add(p.getX(), p.getY());
                }
            }
        }
        int pad = (int) INK_WIDTH + 4;
        bounds.grow(pad, pad);
        bounds.translate(moveDx, moveDy);
        return bounds;
    }

    // ---------------------------------------------------------------- strokes and undo

    public List<Stroke> getStrokes() {
        return Collections.unmodifiableList(strokes);
    }

    public boolean isBlank() {
        return strokes.isEmpty();
    }

    /** Steps back one change: a stroke, an erase, a lasso move or delete, or Clear. */
    public void undo() {
        if (!history.isEmpty()) {
            replaceStrokes(history.pop());
            currentStroke = null;
            selection.clear();
            repaint();
        }
    }

    public void clear() {
        if (strokes.isEmpty()) {
            return;
        }
        saveForUndo();
        strokes.clear();
        currentStroke = null;
        selection.clear();
        repaint();
    }

    private void saveForUndo() {
        history.push(new ArrayList<>(strokes));
        if (history.size() > HISTORY_LIMIT) {
            history.removeLast();
        }
    }

    private void replaceStrokes(List<Stroke> newStrokes) {
        List<Stroke> copy = new ArrayList<>(newStrokes);
        strokes.clear();
        strokes.addAll(copy);
    }

    // ---------------------------------------------------------------- images

    /** Renders the ink as dark strokes on a white background, the size of the canvas. */
    public BufferedImage toImage() {
        int width = Math.max(1, getWidth());
        int height = Math.max(1, getHeight());
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, width, height);
        drawStrokes(g2);
        g2.dispose();
        return image;
    }

    /**
     * Screenshot of just the formula: the ink cropped to its bounding box with a white margin,
     * dark strokes on white. This is the image handed to handwriting recognition.
     *
     * @throws IllegalStateException if nothing has been written
     */
    public BufferedImage toFormulaImage() {
        if (isBlank()) {
            throw new IllegalStateException("The canvas is empty.");
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Stroke stroke : strokes) {
            for (InkPoint point : stroke.getPoints()) {
                minX = Math.min(minX, point.getX());
                minY = Math.min(minY, point.getY());
                maxX = Math.max(maxX, point.getX());
                maxY = Math.max(maxY, point.getY());
            }
        }
        int margin = FORMULA_MARGIN + (int) Math.ceil(INK_WIDTH / 2);
        int width = maxX - minX + 1 + 2 * margin;
        int height = maxY - minY + 1 + 2 * margin;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, width, height);
        g2.translate(margin - minX, margin - minY);
        drawStrokes(g2);
        g2.dispose();
        return image;
    }

    /** Draws the ink (no background) in canvas coordinates, e.g. onto a copy of the canvas elsewhere. */
    public void paintInk(Graphics2D g2) {
        Graphics2D copy = (Graphics2D) g2.create();
        drawStrokes(copy);
        copy.dispose();
    }

    // ---------------------------------------------------------------- painting

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        setUpInk(g2);
        for (Stroke stroke : strokes) {
            if (!selection.contains(stroke)) {
                drawStroke(g2, stroke);
            }
        }
        if (!selection.isEmpty()) {
            paintSelection(g2);
        }
        if (lassoPath != null && lassoPath.size() > 1) {
            paintLasso(g2);
        }
        if (tool == Tool.ERASER && eraserAt != null) {
            int r = eraserRadius();
            g2.setStroke(new BasicStroke(1.2f));
            g2.setColor(eraserMode == EraserMode.PRECISE ? ERASER_OUTLINE : STROKE_ERASER_OUTLINE);
            g2.draw(new Ellipse2D.Double(eraserAt.x - r, eraserAt.y - r, 2 * r, 2 * r));
        }
        g2.dispose();
    }

    private void paintSelection(Graphics2D g2) {
        Graphics2D moved = (Graphics2D) g2.create();
        moved.translate(moveDx, moveDy);
        moved.setStroke(new BasicStroke(INK_WIDTH + 6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        moved.setColor(SELECTION_HALO);
        for (Stroke stroke : selection) {
            drawStroke(moved, stroke);
        }
        setUpInk(moved);
        for (Stroke stroke : selection) {
            drawStroke(moved, stroke);
        }
        moved.dispose();

        g2.setStroke(DASHED);
        g2.setColor(SELECTION);
        Rectangle bounds = selectionBounds();
        g2.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);
    }

    private void paintLasso(Graphics2D g2) {
        Path2D loop = new Path2D.Double();
        loop.moveTo(lassoPath.get(0).getX(), lassoPath.get(0).getY());
        for (InkPoint p : lassoPath) {
            loop.lineTo(p.getX(), p.getY());
        }
        loop.closePath();
        g2.setColor(SELECTION_HALO);
        g2.fill(loop);
        g2.setStroke(DASHED);
        g2.setColor(SELECTION);
        g2.draw(loop);
    }

    private void drawStrokes(Graphics2D g2) {
        setUpInk(g2);
        for (Stroke stroke : strokes) {
            drawStroke(g2, stroke);
        }
    }

    private static void setUpInk(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setStroke(new BasicStroke(INK_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(INK_COLOR);
    }

    private static void drawStroke(Graphics2D g2, Stroke stroke) {
        List<InkPoint> points = stroke.getPoints();
        if (points.isEmpty()) {
            return;
        }
        // A single point (a tap, e.g. a decimal point) is drawn as a dot by the round cap.
        InkPoint previous = points.get(0);
        for (InkPoint point : points) {
            g2.drawLine(previous.getX(), previous.getY(), point.getX(), point.getY());
            previous = point;
        }
    }
}
