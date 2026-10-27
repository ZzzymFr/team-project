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
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * An infinite white canvas the user writes on with the mouse or a pen; keeps the ink as strokes.
 *
 * <p>The ink lives in an unbounded "world"; the panel is a window onto it that can be moved and
 * zoomed. Scroll to move up and down, Shift+scroll to move sideways, drag with the right or middle
 * button (or with Space held) to move freely, and Ctrl+scroll to zoom around the mouse pointer.
 * Strokes are stored in world coordinates, so moving or zooming never changes the ink.
 *
 * <p>Three tools: the pen writes, the eraser removes ink, and the lasso selects the strokes it is
 * drawn around. The eraser has two modes: precise rubs out only the ink under it (cutting strokes
 * where it passes), stroke removes every stroke it touches. Drag a lasso selection to move it,
 * drag one of its corner handles to resize it (keeping its shape, around the opposite corner),
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
    /** Property fired when the zoom changes (a double; 1 = 100%). */
    public static final String ZOOM_PROPERTY = "zoom";

    public static final double MIN_ZOOM = 0.25;
    public static final double MAX_ZOOM = 4;

    private static final float INK_WIDTH = 4f;
    private static final Color INK_COLOR = Color.BLACK;
    private static final int FORMULA_MARGIN = 16;  // white space kept around the ink in toFormulaImage()
    /** toFormulaImage() is always at least this many times as wide as it is tall. */
    public static final double FORMULA_MIN_ASPECT = 2.0;
    private static final int PRECISE_ERASER_RADIUS = 12;  // on screen, whatever the zoom
    private static final int STROKE_ERASER_RADIUS = 6;  // only has to touch a stroke
    private static final Color STROKE_ERASER_OUTLINE = new Color(220, 38, 38);
    private static final int HISTORY_LIMIT = 100;
    private static final Color SELECTION = new Color(37, 99, 235);
    private static final Color SELECTION_HALO = new Color(37, 99, 235, 60);
    private static final Color ERASER_OUTLINE = new Color(100, 116, 139);
    private static final int GRID_SPACING = 24;      // world units between grid dots
    private static final Color GRID_DOT = new Color(203, 213, 225);
    private static final double SCROLL_STEP = 48;    // screen pixels per wheel notch
    private static final double ZOOM_STEP = 1.1;     // zoom factor per wheel notch
    private static final int HANDLE_SIZE = 8;        // selection corner handles, on screen
    private static final int HANDLE_REACH = 9;       // how close (screen px) a press must be to grab one
    private static final double MIN_RESIZE = 0.1;
    private static final double MAX_RESIZE = 20;
    private static final int[] HANDLE_CURSORS = {Cursor.NW_RESIZE_CURSOR, Cursor.NE_RESIZE_CURSOR,
        Cursor.SE_RESIZE_CURSOR, Cursor.SW_RESIZE_CURSOR};

    private final List<Stroke> strokes = new ArrayList<>();
    private final Deque<List<Stroke>> history = new ArrayDeque<>();
    private Tool tool = Tool.PEN;
    private EraserMode eraserMode = EraserMode.PRECISE;
    private Stroke currentStroke;

    // view: screen = world * scale + origin
    private double scale = 1;
    private double originX;
    private double originY;
    private Point panFrom;            // last mouse position of a pan drag; null when not panning
    private boolean spaceDown;        // Space held: left drag pans

    // eraser
    private Point eraserAt;           // where the eraser outline is drawn (screen); null when the mouse is away
    private Point2D lastErasePoint;   // previous drag position (world), to erase along the path
    private boolean savedBeforeErase; // one undo step per eraser drag

    // lasso (world coordinates)
    private List<InkPoint> lassoPath; // the loop being drawn
    private final Set<Stroke> selection = new HashSet<>();
    private Point2D moveStart;        // where a drag of the selection started
    private int moveDx;
    private int moveDy;
    private int resizeHandle = -1;    // corner being dragged to resize (0 top-left, clockwise), or -1
    private Point2D resizeAnchor;     // the opposite corner, which stays put
    private Point2D resizeCorner;     // where the dragged corner started
    private double resizeFactor = 1;

    public DrawingCanvas() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(900, 500));
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();  // so Delete, Ctrl+Z and Space reach the canvas
                if (spaceDown || e.getButton() != MouseEvent.BUTTON1) {
                    panFrom = e.getPoint();
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    return;
                }
                switch (tool) {
                    case PEN:
                        startStroke(e);
                        break;
                    case ERASER:
                        savedBeforeErase = false;
                        lastErasePoint = toWorld(e.getPoint());
                        eraseAlong(toWorld(e.getPoint()));
                        break;
                    default:
                        startLasso(e);
                        break;
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (panFrom != null) {
                    panBy(e.getX() - panFrom.x, e.getY() - panFrom.y);
                    panFrom = e.getPoint();
                    return;
                }
                switch (tool) {
                    case PEN:
                        if (currentStroke != null) {
                            record(e);
                        }
                        break;
                    case ERASER:
                        eraserAt = e.getPoint();
                        eraseAlong(toWorld(e.getPoint()));
                        break;
                    default:
                        dragLasso(e);
                        break;
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (panFrom != null) {
                    panFrom = null;
                    setCursor(Cursor.getPredefinedCursor(spaceDown ? Cursor.MOVE_CURSOR : Cursor.CROSSHAIR_CURSOR));
                    return;
                }
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
                } else if (tool == Tool.LASSO && !spaceDown) {
                    int handle = handleAt(e.getPoint());
                    Rectangle bounds = selectionBounds();
                    boolean overSelection = bounds != null && bounds.contains(toWorld(e.getPoint()));
                    int cursor = handle >= 0 ? HANDLE_CURSORS[handle]
                            : overSelection ? Cursor.MOVE_CURSOR : Cursor.CROSSHAIR_CURSOR;
                    setCursor(Cursor.getPredefinedCursor(cursor));
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (eraserAt != null) {
                    eraserAt = null;
                    repaint();
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                double notches = e.getPreciseWheelRotation();
                if (e.isControlDown()) {
                    zoomAt(e.getX(), e.getY(), Math.pow(ZOOM_STEP, -notches));
                } else if (e.isShiftDown()) {
                    panBy(-notches * SCROLL_STEP, 0);
                } else {
                    panBy(0, -notches * SCROLL_STEP);
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
        bindKey("DELETE", "deleteSelection", this::deleteSelection);
        bindKey("BACK_SPACE", "deleteSelection", this::deleteSelection);
        bindKey("ESCAPE", "clearSelection", this::clearSelection);
        bindKey("ctrl Z", "undo", this::undo);
        bindKey("pressed SPACE", "panOn", () -> setSpaceDown(true));
        bindKey("released SPACE", "panOff", () -> setSpaceDown(false));
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

    private void setSpaceDown(boolean down) {
        spaceDown = down;
        if (panFrom == null) {
            setCursor(Cursor.getPredefinedCursor(down ? Cursor.MOVE_CURSOR : Cursor.CROSSHAIR_CURSOR));
        }
    }

    // ---------------------------------------------------------------- view: pan and zoom

    /** The zoom: 1 = 100%. */
    public double getZoom() {
        return scale;
    }

    /** Moves the view by (dx, dy) screen pixels; the ink moves with it. */
    public void panBy(double dx, double dy) {
        originX += dx;
        originY += dy;
        repaint();
    }

    /** Zooms by a factor, keeping the world point under (screenX, screenY) in place. */
    public void zoomAt(double screenX, double screenY, double factor) {
        double old = scale;
        double zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, scale * factor));
        if (zoom == old) {
            return;
        }
        double worldX = (screenX - originX) / old;
        double worldY = (screenY - originY) / old;
        scale = zoom;
        originX = screenX - worldX * zoom;
        originY = screenY - worldY * zoom;
        repaint();
        firePropertyChange(ZOOM_PROPERTY, old, zoom);
    }

    /** Back to 100%, with the ink (if any) in the middle of the view. */
    public void resetView() {
        double old = scale;
        scale = 1;
        Rectangle ink = inkBounds();
        if (ink == null) {
            originX = 0;
            originY = 0;
        } else {
            originX = getWidth() / 2.0 - ink.getCenterX();
            originY = getHeight() / 2.0 - ink.getCenterY();
        }
        repaint();
        firePropertyChange(ZOOM_PROPERTY, old, scale);
    }

    private Point2D toWorld(Point screen) {
        return new Point2D.Double((screen.x - originX) / scale, (screen.y - originY) / scale);
    }

    private InkPoint worldPoint(MouseEvent e) {
        Point2D p = toWorld(e.getPoint());
        return new InkPoint((int) Math.round(p.getX()), (int) Math.round(p.getY()), e.getWhen());
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

    /** The eraser's radius on screen; it stays the same size however far you zoom. */
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
        currentStroke.addPoint(worldPoint(e));
        repaint();
    }

    private void eraseAlong(Point2D to) {
        Point2D from = lastErasePoint == null ? to : lastErasePoint;
        double radius = eraserRadius() / scale;
        List<Stroke> after = eraserMode == EraserMode.PRECISE
                ? InkEditor.eraseAlong(strokes, from.getX(), from.getY(), to.getX(), to.getY(), radius)
                : InkEditor.removeTouchedAlong(strokes, from.getX(), from.getY(), to.getX(), to.getY(), radius);
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
        Point2D p = toWorld(e.getPoint());
        int handle = handleAt(e.getPoint());
        if (handle >= 0) {
            resizeHandle = handle;
            resizeCorner = corner(bounds, handle);
            resizeAnchor = corner(bounds, (handle + 2) % 4);
            resizeFactor = 1;
            return;
        }
        if (bounds != null && bounds.contains(p)) {
            moveStart = p;
            moveDx = 0;
            moveDy = 0;
            return;
        }
        clearSelection();
        lassoPath = new ArrayList<>();
        lassoPath.add(worldPoint(e));
    }

    private void dragLasso(MouseEvent e) {
        if (resizeHandle >= 0) {
            // How far along the anchor-to-corner diagonal the mouse is: 1 = where it started.
            Point2D p = toWorld(e.getPoint());
            double vx = resizeCorner.getX() - resizeAnchor.getX();
            double vy = resizeCorner.getY() - resizeAnchor.getY();
            double along = ((p.getX() - resizeAnchor.getX()) * vx + (p.getY() - resizeAnchor.getY()) * vy)
                    / (vx * vx + vy * vy);
            resizeFactor = Math.max(MIN_RESIZE, Math.min(MAX_RESIZE, along));
        } else if (moveStart != null) {
            Point2D p = toWorld(e.getPoint());
            moveDx = (int) Math.round(p.getX() - moveStart.getX());
            moveDy = (int) Math.round(p.getY() - moveStart.getY());
        } else if (lassoPath != null) {
            lassoPath.add(worldPoint(e));
        }
        repaint();
    }

    private void finishLasso() {
        if (resizeHandle >= 0) {
            if (resizeFactor != 1) {
                Point2D anchor = resizeAnchor;
                double factor = resizeFactor;
                transformSelection(s -> InkEditor.scale(s, anchor.getX(), anchor.getY(), factor));
            }
            resizeHandle = -1;
            resizeFactor = 1;
        } else if (moveStart != null) {
            if (moveDx != 0 || moveDy != 0) {
                int dx = moveDx;
                int dy = moveDy;
                transformSelection(s -> InkEditor.translate(s, dx, dy));
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

    /** Replaces each selected stroke with its changed copy, as one undo step; the copies stay selected. */
    private void transformSelection(UnaryOperator<Stroke> change) {
        saveForUndo();
        List<Stroke> after = new ArrayList<>();
        Set<Stroke> changed = new HashSet<>();
        for (Stroke stroke : strokes) {
            if (selection.contains(stroke)) {
                Stroke copy = change.apply(stroke);
                after.add(copy);
                changed.add(copy);
            } else {
                after.add(stroke);
            }
        }
        replaceStrokes(after);
        selection.clear();
        selection.addAll(changed);
    }

    /** The selected strokes as they look right now, including a move or resize being dragged. */
    private List<Stroke> selectionPreview() {
        List<Stroke> preview = new ArrayList<>();
        for (Stroke stroke : selection) {
            if (resizeHandle >= 0 && resizeFactor != 1) {
                preview.add(InkEditor.scale(stroke, resizeAnchor.getX(), resizeAnchor.getY(), resizeFactor));
            } else if (moveDx != 0 || moveDy != 0) {
                preview.add(InkEditor.translate(stroke, moveDx, moveDy));
            } else {
                preview.add(stroke);
            }
        }
        return preview;
    }

    /** Corner i of a box: 0 top-left, 1 top-right, 2 bottom-right, 3 bottom-left. */
    private static Point2D corner(Rectangle box, int i) {
        double x = i == 0 || i == 3 ? box.getMinX() : box.getMaxX();
        double y = i < 2 ? box.getMinY() : box.getMaxY();
        return new Point2D.Double(x, y);
    }

    /** The selection corner handle under a screen point, or -1. */
    private int handleAt(Point screen) {
        Rectangle bounds = selectionBounds();
        if (bounds == null) {
            return -1;
        }
        for (int i = 0; i < 4; i++) {
            Point2D c = corner(bounds, i);
            double sx = c.getX() * scale + originX;
            double sy = c.getY() * scale + originY;
            if (Math.abs(screen.x - sx) <= HANDLE_REACH && Math.abs(screen.y - sy) <= HANDLE_REACH) {
                return i;
            }
        }
        return -1;
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

    /** The selection's outline in world coordinates (including a drag in progress), or null. */
    private Rectangle selectionBounds() {
        if (selection.isEmpty()) {
            return null;
        }
        Rectangle bounds = boundsOf(selectionPreview());
        int pad = (int) INK_WIDTH + 4;
        bounds.grow(pad, pad);
        return bounds;
    }

    /** The box around all the ink's points (world coordinates), or null if there is no ink. */
    private Rectangle inkBounds() {
        return strokes.isEmpty() ? null : boundsOf(strokes);
    }

    private static Rectangle boundsOf(Iterable<Stroke> strokeSet) {
        Rectangle bounds = null;
        for (Stroke stroke : strokeSet) {
            for (InkPoint p : stroke.getPoints()) {
                if (bounds == null) {
                    bounds = new Rectangle(p.getX(), p.getY(), 0, 0);
                } else {
                    bounds.add(p.getX(), p.getY());
                }
            }
        }
        return bounds;
    }

    // ---------------------------------------------------------------- strokes and undo

    /** The strokes, in world coordinates. */
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

    /** Renders the ink as dark strokes on a white background: what the canvas shows, the size of the canvas. */
    public BufferedImage toImage() {
        int width = Math.max(1, getWidth());
        int height = Math.max(1, getHeight());
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, width, height);
        paintInk(g2);
        g2.dispose();
        return image;
    }

    /**
     * Screenshot of just the formula: all the ink, cropped to its bounding box with a white margin,
     * dark strokes on white, at 100% whatever the current zoom. This is the image handed to
     * handwriting recognition.
     *
     * <p>The image is always a landscape rectangle, at least {@value #FORMULA_MIN_ASPECT} times as
     * wide as it is tall, like a line of formula; narrow ink (a single tall symbol, a fraction) is
     * centred with extra white space on its left and right.
     *
     * @throws IllegalStateException if nothing has been written
     */
    public BufferedImage toFormulaImage() {
        if (isBlank()) {
            throw new IllegalStateException("The canvas is empty.");
        }
        Rectangle ink = inkBounds();
        int margin = FORMULA_MARGIN + (int) Math.ceil(INK_WIDTH / 2);
        int inkWidth = ink.width + 1 + 2 * margin;
        int height = ink.height + 1 + 2 * margin;
        int width = Math.max(inkWidth, (int) Math.ceil(FORMULA_MIN_ASPECT * height));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, width, height);
        g2.translate(margin - ink.x + (width - inkWidth) / 2, margin - ink.y);
        drawStrokes(g2);
        g2.dispose();
        return image;
    }

    /** Draws the ink (no background) as it appears on screen, e.g. onto a copy of the canvas elsewhere. */
    public void paintInk(Graphics2D g2) {
        Graphics2D world = (Graphics2D) g2.create();
        world.translate(originX, originY);
        world.scale(scale, scale);
        drawStrokes(world);
        world.dispose();
    }

    /** Draws the grid and the ink (no background) as they appear on screen. */
    public void paintView(Graphics2D g2) {
        paintGrid(g2);
        paintInk(g2);
    }

    // ---------------------------------------------------------------- painting

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        paintGrid(g2);

        Graphics2D world = (Graphics2D) g2.create();
        world.translate(originX, originY);
        world.scale(scale, scale);
        setUpInk(world);
        for (Stroke stroke : strokes) {
            if (!selection.contains(stroke)) {
                drawStroke(world, stroke);
            }
        }
        if (!selection.isEmpty()) {
            paintSelection(world);
        }
        if (lassoPath != null && lassoPath.size() > 1) {
            paintLasso(world);
        }
        world.dispose();

        if (tool == Tool.ERASER && eraserAt != null) {
            int r = eraserRadius();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(1.2f));
            g2.setColor(eraserMode == EraserMode.PRECISE ? ERASER_OUTLINE : STROKE_ERASER_OUTLINE);
            g2.draw(new Ellipse2D.Double(eraserAt.x - r, eraserAt.y - r, 2 * r, 2 * r));
        }
        g2.dispose();
    }

    /** Light dots that move and zoom with the world, so you can see the canvas moving. */
    private void paintGrid(Graphics2D g2) {
        double spacing = GRID_SPACING * scale;
        while (spacing < 14) {
            spacing *= 2;  // zoomed far out: fewer dots
        }
        double startX = floorMod(originX, spacing);
        double startY = floorMod(originY, spacing);
        g2.setColor(GRID_DOT);
        for (double x = startX; x < getWidth(); x += spacing) {
            for (double y = startY; y < getHeight(); y += spacing) {
                g2.fillRect((int) Math.round(x) - 1, (int) Math.round(y) - 1, 2, 2);
            }
        }
    }

    private static double floorMod(double a, double m) {
        return ((a % m) + m) % m;
    }

    /** A dashed line that looks the same at every zoom. */
    private BasicStroke dashed() {
        float unit = (float) (1 / scale);
        return new BasicStroke(1.2f * unit, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f,
                new float[]{5f * unit, 4f * unit}, 0f);
    }

    private void paintSelection(Graphics2D world) {
        List<Stroke> preview = selectionPreview();
        Graphics2D ink = (Graphics2D) world.create();
        ink.setStroke(new BasicStroke(INK_WIDTH + 6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        ink.setColor(SELECTION_HALO);
        for (Stroke stroke : preview) {
            drawStroke(ink, stroke);
        }
        setUpInk(ink);
        for (Stroke stroke : preview) {
            drawStroke(ink, stroke);
        }
        ink.dispose();

        world.setStroke(dashed());
        world.setColor(SELECTION);
        Rectangle bounds = selectionBounds();
        world.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);

        // corner handles for resizing, the same size on screen at every zoom
        double size = HANDLE_SIZE / scale;
        world.setStroke(new BasicStroke((float) (1.5 / scale)));
        for (int i = 0; i < 4; i++) {
            Point2D c = corner(bounds, i);
            Rectangle2D handle = new Rectangle2D.Double(c.getX() - size / 2, c.getY() - size / 2, size, size);
            world.setColor(Color.WHITE);
            world.fill(handle);
            world.setColor(SELECTION);
            world.draw(handle);
        }
    }

    private void paintLasso(Graphics2D world) {
        Path2D loop = new Path2D.Double();
        loop.moveTo(lassoPath.get(0).getX(), lassoPath.get(0).getY());
        for (InkPoint p : lassoPath) {
            loop.lineTo(p.getX(), p.getY());
        }
        loop.closePath();
        world.setColor(SELECTION_HALO);
        world.fill(loop);
        world.setStroke(dashed());
        world.setColor(SELECTION);
        world.draw(loop);
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
