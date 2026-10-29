package view;

import entity.AnswerKind;
import entity.AnswerLayout;
import entity.CanvasAnswer;
import entity.Formula;
import entity.FormulaBook;
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
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.UnaryOperator;

/**
 * An infinite white canvas the user writes on with the mouse or a pen; keeps the ink as strokes.
 *
 * <p>The ink lives in an unbounded "world"; the panel is a window onto it that can be moved and
 * zoomed. Scroll to move up and down, Shift+scroll to move sideways, drag with the right or middle
 * button (or with Space held) to move freely, and Ctrl+scroll to zoom around the mouse pointer.
 * Strokes are stored in world coordinates, so moving or zooming never changes the ink.
 * A picture such as the graph of the formula can be shown as a card beside the ink ({@link #showGraph}).
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
    private static final Color GRAPH_CARD_BORDER = new Color(203, 213, 225);
    private static final double GRAPH_CARD_WIDTH = 0.4;   // of the visible canvas width
    private static final int GRAPH_CARD_PADDING = 10;     // white border around the graph, on screen
    private static final int GRAPH_CARD_GAP = 48;         // space between the formula and the card, on screen
    private static final int GRAPH_CLOSE_RADIUS = 11;     // close button, on screen
    private static final int VIEW_MARGIN_X = 16;          // keep a new card this far inside the view...
    private static final int VIEW_MARGIN_Y = 60;          // ...and clear of the floating controls
    private static final Color ANSWER_ERROR = new Color(220, 38, 38);
    private static final int[] HANDLE_CURSORS = {Cursor.NW_RESIZE_CURSOR, Cursor.NE_RESIZE_CURSOR,
        Cursor.SE_RESIZE_CURSOR, Cursor.SW_RESIZE_CURSOR};

    private final List<Stroke> strokes = new ArrayList<>();
    private final Deque<Snapshot> history = new ArrayDeque<>();
    private FormulaBook book = new FormulaBook();  // calculated formulas + their answers
    private long revisions;                       // only goes up, undo too
    private final List<LongConsumer> formulaListeners = new ArrayList<>();
    private final List<Consumer<Stroke>> strokeListeners = new ArrayList<>();
    private final List<Runnable> actionListeners = new ArrayList<>();  // user started doing something
    private final List<Runnable> viewListeners = new ArrayList<>();    // pan / zoom
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
    private final Set<Long> selectedAnswers = new HashSet<>();  // formula ids
    private Point2D moveStart;        // where a drag of the selection started
    private int moveDx;
    private int moveDy;
    // graph card: a picture shown next to the formula, in world coordinates so it pans and zooms with the ink
    private BufferedImage graphImage;
    private Rectangle2D graphCard;
    private double graphPadding;
    private boolean overGraphClose;

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
                if (e.getButton() == MouseEvent.BUTTON1 && graphCloseContains(e.getPoint())) {
                    removeGraph();
                    return;
                }
                if (spaceDown || e.getButton() != MouseEvent.BUTTON1) {
                    panFrom = e.getPoint();
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    return;
                }
                fire(actionListeners);
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
                if (currentStroke != null) {
                    Stroke done = currentStroke;
                    currentStroke = null;
                    inkChanged(List.of(), List.of(done));  // one check per stroke
                    for (Consumer<Stroke> l : new ArrayList<>(strokeListeners)) {
                        l.accept(done);
                    }
                }
                lastErasePoint = null;
                if (tool == Tool.LASSO) {
                    finishLasso();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                boolean onClose = graphCloseContains(e.getPoint());
                if (onClose != overGraphClose) {
                    overGraphClose = onClose;
                    setCursor(Cursor.getPredefinedCursor(onClose ? Cursor.HAND_CURSOR : Cursor.CROSSHAIR_CURSOR));
                    repaint();
                }
                if (onClose) {
                    return;
                }
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
        fire(viewListeners);
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
        fire(viewListeners);
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
        fire(viewListeners);
    }

    // ---------------------------------------------------------------- graph card

    /** Shows a graph card beside the formula as it is now (the lasso selection, or all the ink). */
    public void showGraph(BufferedImage image) {
        showGraph(image, getFormulaBounds());
    }

    /**
     * Shows a picture (the graph of a written function) as a card beside the given formula: to its
     * right, centred on it vertically, 40% of the visible width, so it never fills the canvas.
     * The view moves if needed so the card can be seen. The card replaces any earlier one, moves
     * and zooms with the ink, closes with its × button, and is never part of the formula image.
     *
     * @param formula where the formula is, in world coordinates ({@link #getFormulaBounds()} when it
     *                was captured); null puts the card in the middle of the view
     */
    public void showGraph(BufferedImage image, Rectangle formula) {
        double viewWidth = (getWidth() > 0 ? getWidth() : getPreferredSize().width) / scale;
        double imageWidth = viewWidth * GRAPH_CARD_WIDTH;
        double imageHeight = imageWidth * image.getHeight() / image.getWidth();
        graphImage = downscaled(image, (int) Math.ceil(imageWidth * 2));  // sharp up to 200% (and on HiDPI)
        graphPadding = GRAPH_CARD_PADDING / scale;
        double w = imageWidth + 2 * graphPadding;
        double h = imageHeight + 2 * graphPadding;

        double x;
        double y;
        if (formula != null) {
            x = formula.getMaxX() + GRAPH_CARD_GAP / scale;
            y = formula.getCenterY() - h / 2;
        } else {
            x = (getWidth() / 2.0 - originX) / scale - w / 2;
            y = (getHeight() / 2.0 - originY) / scale - h / 2;
        }
        graphCard = new Rectangle2D.Double(x, y, w, h);
        bringIntoView(formula == null ? graphCard : graphCard.createUnion(formula), graphCard);
        repaint();
    }

    public void removeGraph() {
        if (graphCard == null) {
            return;
        }
        graphImage = null;
        graphCard = null;
        overGraphClose = false;
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        repaint();
    }

    public boolean hasGraph() {
        return graphCard != null;
    }

    /** Where the graph card is on screen right now, or null if there is none. */
    public Rectangle2D getGraphCardOnScreen() {
        return graphCard == null ? null : toScreen(graphCard);
    }

    /** The point to click to close the graph card, on screen, or null if there is none. */
    public Point2D getGraphCloseOnScreen() {
        if (graphCard == null) {
            return null;
        }
        Rectangle2D card = toScreen(graphCard);
        return new Point2D.Double(card.getMaxX() - GRAPH_CLOSE_RADIUS - 5, card.getMinY() + GRAPH_CLOSE_RADIUS + 5);
    }

    private boolean graphCloseContains(Point p) {
        Point2D close = getGraphCloseOnScreen();
        return close != null && close.distance(p) <= GRAPH_CLOSE_RADIUS + 2;
    }

    /** Pans (without zooming) so the world rectangle, or at least {@code fallback}, is in view. */
    private void bringIntoView(Rectangle2D world, Rectangle2D fallback) {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        double left = VIEW_MARGIN_X;
        double right = getWidth() - VIEW_MARGIN_X;
        double top = VIEW_MARGIN_Y;
        double bottom = getHeight() - VIEW_MARGIN_Y;
        Rectangle2D target = toScreen(world);
        if (target.getWidth() > right - left || target.getHeight() > bottom - top) {
            target = toScreen(fallback);  // too big: just show the fallback
        }
        panBy(shiftInto(target.getMinX(), target.getMaxX(), left, right),
                shiftInto(target.getMinY(), target.getMaxY(), top, bottom));
    }

    /** How far to move [min, max] to bring it inside [low, high]; the start wins if it cannot fit. */
    private static double shiftInto(double min, double max, double low, double high) {
        if (min < low) {
            return low - min;
        }
        if (max > high) {
            return Math.max(high - max, low - min);
        }
        return 0;
    }

    // world box -> where it is on screen now
    public Rectangle2D worldToScreen(Rectangle2D world) {
        return toScreen(world);
    }

    private Rectangle2D toScreen(Rectangle2D world) {
        return new Rectangle2D.Double(world.getX() * scale + originX, world.getY() * scale + originY,
                world.getWidth() * scale, world.getHeight() * scale);
    }

    /** A smooth, smaller copy of a big image (QuickChart sends 1600 px), so it draws cleanly. */
    private static BufferedImage downscaled(BufferedImage image, int maxWidth) {
        if (image.getWidth() <= maxWidth || maxWidth <= 0) {
            return image;
        }
        int height = Math.max(1, (int) Math.round((double) image.getHeight() * maxWidth / image.getWidth()));
        BufferedImage small = new BufferedImage(maxWidth, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = small.createGraphics();
        g.drawImage(image.getScaledInstance(maxWidth, height, java.awt.Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();
        return small;
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
        fire(actionListeners);
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
        fire(actionListeners);
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
        List<Long> rubbed = new ArrayList<>();
        for (Formula f : book.all()) {
            CanvasAnswer a = f.getAnswer();
            if (a != null) {
                Rectangle2D hit = a.getBox();
                hit.setRect(hit.getX() - radius, hit.getY() - radius, hit.getWidth() + 2 * radius, hit.getHeight() + 2 * radius);
                if (hit.intersectsLine(from.getX(), from.getY(), to.getX(), to.getY())) {
                    rubbed.add(f.getId());
                }
            }
        }
        if ((after != strokes || !rubbed.isEmpty()) && !savedBeforeErase) {
            saveForUndo();
            savedBeforeErase = true;
        }
        for (long id : rubbed) {
            book.remove(id);  // whole answer goes, formula unbound
        }
        if (after != strokes) {
            List<Stroke> before = new ArrayList<>(strokes);
            replaceStrokes(after);
            inkChanged(minus(before, after), minus(after, before));
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
                transformSelection(s -> InkEditor.scale(s, anchor.getX(), anchor.getY(), factor),
                        a -> a.scaled(anchor.getX(), anchor.getY(), factor));
            }
            resizeHandle = -1;
            resizeFactor = 1;
        } else if (moveStart != null) {
            if (moveDx != 0 || moveDy != 0) {
                int dx = moveDx;
                int dy = moveDy;
                transformSelection(s -> InkEditor.translate(s, dx, dy), a -> a.translated(dx, dy));
            }
            moveStart = null;
            moveDx = 0;
            moveDy = 0;
        } else if (lassoPath != null) {
            selection.addAll(InkEditor.selectInside(strokes, lassoPath));
            if (lassoPath.size() >= 3) {
                for (Formula f : book.all()) {
                    CanvasAnswer a = f.getAnswer();
                    if (a != null && InkEditor.isInside(lassoPath, a.getBox().getCenterX(), a.getBox().getCenterY())) {
                        selectedAnswers.add(f.getId());
                    }
                }
            }
            lassoPath = null;
        }
        repaint();
    }

    /** Replaces each selected stroke with its changed copy, as one undo step; the copies stay selected. */
    private void transformSelection(UnaryOperator<Stroke> change, UnaryOperator<CanvasAnswer> answerChange) {
        saveForUndo();
        List<Stroke> after = new ArrayList<>();
        Map<Stroke, Stroke> copies = new HashMap<>();
        for (Stroke stroke : strokes) {
            if (selection.contains(stroke)) {
                Stroke copy = change.apply(stroke);
                after.add(copy);
                copies.put(stroke, copy);
            } else {
                after.add(stroke);
            }
        }
        replaceStrokes(after);
        // whole formula moved: same content, answer tags along
        List<Stroke> removed = new ArrayList<>(selection);
        Set<Long> moved = new HashSet<>();
        for (Formula f : book.all()) {
            if (selection.containsAll(f.getStrokes())) {
                book.moveWhole(f.getId(), copies, answerChange);
                removed.removeAll(f.getStrokes());
                moved.add(f.getId());
            }
        }
        for (long id : selectedAnswers) {
            Formula f = book.get(id);
            if (f != null && f.getAnswer() != null && !moved.contains(id)) {
                book.put(f.withAnswer(answerChange.apply(f.getAnswer())));
            }
        }
        List<Stroke> added = new ArrayList<>();
        for (Stroke s : removed) {
            added.add(copies.get(s));
        }
        inkChanged(removed, added);  // partly moved formulas count as edited
        selection.clear();
        selection.addAll(copies.values());
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

    // selected answers as they look right now, drag included
    private List<CanvasAnswer> answerPreview() {
        List<CanvasAnswer> preview = new ArrayList<>();
        for (long id : selectedAnswers) {
            Formula f = book.get(id);
            CanvasAnswer a = f == null ? null : f.getAnswer();
            if (a == null) {
                continue;
            }
            if (resizeHandle >= 0 && resizeFactor != 1) {
                a = a.scaled(resizeAnchor.getX(), resizeAnchor.getY(), resizeFactor);
            } else if (moveDx != 0 || moveDy != 0) {
                a = a.translated(moveDx, moveDy);
            }
            preview.add(a);
        }
        return preview;
    }

    private boolean nothingSelected() {
        return selection.isEmpty() && answerPreview().isEmpty();
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
        return !nothingSelected();
    }

    /** Removes the strokes and answers selected with the lasso. */
    public void deleteSelection() {
        if (nothingSelected()) {
            return;
        }
        fire(actionListeners);
        saveForUndo();
        for (long id : selectedAnswers) {
            book.remove(id);  // unbinds the formula, ink stays
        }
        selectedAnswers.clear();
        List<Stroke> after = new ArrayList<>(strokes);
        after.removeAll(selection);
        replaceStrokes(after);
        inkChanged(new ArrayList<>(selection), List.of());
        selection.clear();
        repaint();
    }

    public void clearSelection() {
        if (!selection.isEmpty() || !selectedAnswers.isEmpty()) {
            selection.clear();
            selectedAnswers.clear();
            repaint();
        }
    }

    /** The selection's outline in world coordinates (including a drag in progress), or null. */
    private Rectangle selectionBounds() {
        if (nothingSelected()) {
            return null;
        }
        Rectangle bounds = boundsOf(selectionPreview());
        for (CanvasAnswer a : answerPreview()) {
            if (bounds == null) {
                bounds = a.getBox().getBounds();
            } else {
                bounds.add(a.getBox().getBounds());
            }
        }
        int pad = (int) INK_WIDTH + 4;
        bounds.grow(pad, pad);
        return bounds;
    }

    /** The box around all the ink's points (world coordinates), or null if there is no ink. */
    private Rectangle inkBounds() {
        return strokes.isEmpty() ? null : boundsOf(strokes);
    }

    private static Rectangle boundsOf(Iterable<Stroke> strokeSet) {
        return AnswerLayout.bounds(strokeSet);
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
            Snapshot back = history.pop();
            replaceStrokes(back.strokes);
            book = back.book;  // answers come back as they were, no recalc
            currentStroke = null;
            selection.clear();
            selectedAnswers.clear();
            repaint();
            for (Formula f : book.all()) {
                if (f.getAnswer() == null) {
                    notifyChanged(f.getId());  // came back without its answer, get one
                }
            }
            fire(actionListeners);  // after the notes above, so listeners can drop them
        }
    }

    public void clear() {
        if (strokes.isEmpty()) {
            return;
        }
        fire(actionListeners);
        saveForUndo();
        strokes.clear();
        for (Formula f : book.all()) {
            book.remove(f.getId());
        }
        currentStroke = null;
        selection.clear();
        selectedAnswers.clear();
        repaint();
    }

    private void saveForUndo() {
        history.push(new Snapshot(new ArrayList<>(strokes), book.copy()));
        if (history.size() > HISTORY_LIMIT) {
            history.removeLast();
        }
    }

    private void replaceStrokes(List<Stroke> newStrokes) {
        List<Stroke> copy = new ArrayList<>(newStrokes);
        strokes.clear();
        strokes.addAll(copy);
    }

    // a - b, by identity
    private static List<Stroke> minus(List<Stroke> a, List<Stroke> b) {
        Set<Stroke> drop = new HashSet<>(b);
        List<Stroke> left = new ArrayList<>();
        for (Stroke s : a) {
            if (!drop.contains(s)) {
                left.add(s);
            }
        }
        return left;
    }

    // tell the book, then whoever listens, which formulas got edited
    private void inkChanged(List<Stroke> removed, List<Stroke> added) {
        if (removed.isEmpty() && added.isEmpty()) {
            return;
        }
        for (long id : book.strokesReplaced(removed, added, this::nextRevision)) {
            notifyChanged(id);
        }
    }

    private void notifyChanged(long id) {
        for (LongConsumer listener : formulaListeners) {
            listener.accept(id);
        }
    }

    private static void fire(List<Runnable> listeners) {
        for (Runnable l : new ArrayList<>(listeners)) {
            l.run();
        }
    }

    // mouse still down on something
    public boolean isBusy() {
        return busy();
    }

    // mid-gesture a pan would shift the ink under the pen
    private boolean busy() {
        return currentStroke != null || moveStart != null || resizeHandle >= 0 || lassoPath != null
                || lastErasePoint != null || panFrom != null;
    }

    private long nextRevision() {
        return ++revisions;
    }

    private static final class Snapshot {
        final List<Stroke> strokes;
        final FormulaBook book;

        Snapshot(List<Stroke> strokes, FormulaBook book) {
            this.strokes = strokes;
            this.book = book;
        }
    }

    // ---------------------------------------------------------------- formulas and answers

    // fires per edited formula id, not on undo
    public void addFormulaChangeListener(LongConsumer listener) {
        formulaListeners.add(listener);
    }

    // pen stroke finished (after the formula check)
    public void addStrokeListener(Consumer<Stroke> listener) {
        strokeListeners.add(listener);
    }

    // left press with a tool, tool/mode switch, undo, clear, delete
    public void addUserActionListener(Runnable listener) {
        actionListeners.add(listener);
    }

    public void addViewListener(Runnable listener) {
        viewListeners.add(listener);
    }

    public boolean isBound(Stroke s) {
        return book.isBound(s);
    }

    // world box of a formula, null if gone
    public Rectangle formulaBounds(long id) {
        Formula f = book.get(id);
        return f == null ? null : boundsOf(f.getStrokes());
    }

    // bind loose ink near last as one formula (other session groups too); null if last gone/bound
    public FormulaCapture registerNewInk(Stroke last, Collection<Stroke> session) {
        if (!strokes.contains(last) || book.isBound(last)) {
            return null;
        }
        // char size from everything written this round, a lone "-" would make it tiny
        List<Stroke> pool = new ArrayList<>();
        for (Stroke s : strokes) {
            if (!book.isBound(s) && (s == last || session.contains(s))) {
                pool.add(s);
            }
        }
        double h = AnswerLayout.strokeHeight(pool);
        Formula mine = book.add(looseGroup(last, h), nextRevision());
        for (Stroke s : session) {
            if (strokes.contains(s) && !book.isBound(s)) {
                book.add(looseGroup(s, h), nextRevision());
            }
        }
        return capture(mine);
    }

    // unbound strokes near seed, grown until nothing else joins
    private Set<Stroke> looseGroup(Stroke seed, double h) {
        Set<Stroke> group = new LinkedHashSet<>(List.of(seed));
        boolean grew = true;
        while (grew) {
            grew = false;
            Rectangle box = boundsOf(group);
            for (Stroke s : strokes) {
                Rectangle b = group.contains(s) || book.isBound(s) ? null : boundsOf(List.of(s));
                if (b != null && box != null && AnswerLayout.sameNewFormula(box, h, b)) {
                    group.add(s);
                    grew = true;
                }
            }
        }
        return group;
    }

    public List<Formula> getFormulas() {
        return book.all();
    }

    public List<CanvasAnswer> getAnswers() {
        List<CanvasAnswer> answers = new ArrayList<>();
        for (Formula f : book.all()) {
            if (f.getAnswer() != null) {
                answers.add(f.getAnswer());
            }
        }
        return answers;
    }

    // lasso pick, else unbound ink, else redo every formula; throws if blank
    public List<FormulaCapture> takeFormulasToCalculate() {
        if (isBlank()) {
            throw new IllegalStateException("The canvas is empty.");
        }
        List<Stroke> picked = formulaStrokes();
        if (selection.isEmpty()) {
            picked = new ArrayList<>();
            for (Stroke s : strokes) {
                if (!book.isBound(s)) {
                    picked.add(s);
                }
            }
        }
        List<FormulaCapture> captures = new ArrayList<>();
        if (!picked.isEmpty()) {
            captures.add(capture(book.add(picked, nextRevision())));
        } else {
            for (Formula f : book.all()) {
                captures.add(capture(book.add(f.getStrokes(), nextRevision())));  // fresh, no answer
            }
        }
        repaint();
        return captures;
    }

    // null if gone or already answered
    public FormulaCapture captureForRecalculation(long id) {
        Formula f = book.get(id);
        return f == null || f.getAnswer() != null ? null : capture(f);
    }

    // false if the formula is gone or changed since capture
    public boolean isCurrent(long id, long revision) {
        Formula f = book.get(id);
        return f != null && f.getRevision() == revision;
    }

    // false if the formula is gone or changed since capture
    public boolean showAnswer(long id, long revision, AnswerKind kind, String text, boolean error) {
        Formula f = book.get(id);
        if (f == null || f.getRevision() != revision) {
            return false;
        }
        List<Stroke> ink = inDrawingOrder(f);
        double h = AnswerLayout.charHeight(ink);
        float size = AnswerFont.sizeFor(h);
        Rectangle2D text2d = AnswerFont.visualBounds(text, size);
        double w = text2d.getWidth();
        double ht = text2d.getHeight();
        Point2D at = kind.isBelow() ? AnswerLayout.below(ink, h) : AnswerLayout.rightOf(ink, h, ht);
        if (kind.isBelow() && hitsOtherInk(new Rectangle2D.Double(at.getX(), at.getY(), w, ht), f)) {
            at = AnswerLayout.rightOf(ink, h, ht);
        }
        CanvasAnswer answer = new CanvasAnswer(text, error, at.getX(), at.getY(), w, ht, size);
        book.put(f.withAnswer(answer));
        if (!busy()) {
            bringIntoView(answer.getBox(), answer.getBox());
        }
        repaint();
        return true;
    }

    private boolean hitsOtherInk(Rectangle2D box, Formula f) {
        for (Stroke s : strokes) {
            Rectangle b = f.getStrokes().contains(s) ? null : boundsOf(List.of(s));
            if (b != null && box.intersects(b.x, b.y, Math.max(1, b.width), Math.max(1, b.height))) {
                return true;
            }
        }
        return false;
    }

    private List<Stroke> inDrawingOrder(Formula f) {
        List<Stroke> ink = new ArrayList<>();
        for (Stroke s : strokes) {
            if (f.getStrokes().contains(s)) {
                ink.add(s);
            }
        }
        return ink;
    }

    private FormulaCapture capture(Formula f) {
        return new FormulaCapture(f.getId(), f.getRevision(), imageOf(inDrawingOrder(f)));
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
     * The strokes that make up "the formula": the lasso selection if there is one (so one formula
     * can be picked out when several are written), otherwise all the ink. In drawing order.
     */
    private List<Stroke> formulaStrokes() {
        if (selection.isEmpty()) {
            return strokes;
        }
        List<Stroke> picked = new ArrayList<>();
        for (Stroke stroke : strokes) {
            if (selection.contains(stroke)) {
                picked.add(stroke);
            }
        }
        return picked;
    }

    /** Where the formula is (world coordinates): the lasso selection, or all the ink; null if blank. */
    public Rectangle getFormulaBounds() {
        List<Stroke> formula = formulaStrokes();
        return formula.isEmpty() ? null : boundsOf(formula);
    }

    /**
     * Screenshot of just the formula: the lasso selection if there is one, otherwise all the ink,
     * cropped to its bounding box with a white margin, dark strokes on white, at 100% whatever the
     * current zoom. This is the image handed to handwriting recognition.
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
        return imageOf(formulaStrokes());
    }

    // cropped picture of some strokes, answers never included
    private BufferedImage imageOf(List<Stroke> formula) {
        Rectangle ink = boundsOf(formula);
        int margin = FORMULA_MARGIN + (int) Math.ceil(INK_WIDTH / 2);
        int inkWidth = ink.width + 1 + 2 * margin;
        int height = ink.height + 1 + 2 * margin;
        int width = Math.max(inkWidth, (int) Math.ceil(FORMULA_MIN_ASPECT * height));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, width, height);
        g2.translate(margin - ink.x + (width - inkWidth) / 2, margin - ink.y);
        setUpInk(g2);
        for (Stroke stroke : formula) {
            drawStroke(g2, stroke);
        }
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

    /** Draws the graph card and the ink (no background) as they appear on screen. */
    public void paintContent(Graphics2D g2) {
        Graphics2D world = (Graphics2D) g2.create();
        world.translate(originX, originY);
        world.scale(scale, scale);
        paintGraphCard(world);
        drawStrokes(world);
        paintAnswers(world);
        world.dispose();
    }

    /** Draws the grid, the graph card and the ink (no background) as they appear on screen. */
    public void paintView(Graphics2D g2) {
        paintGrid(g2);
        paintContent(g2);
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
        paintGraphCard(world);  // under the ink, so writing over it stays visible
        setUpInk(world);
        for (Stroke stroke : strokes) {
            if (!selection.contains(stroke)) {
                drawStroke(world, stroke);
            }
        }
        for (Formula f : book.all()) {
            if (f.getAnswer() != null && !selectedAnswers.contains(f.getId())) {
                drawAnswer(world, f.getAnswer());
            }
        }
        if (!nothingSelected()) {
            paintSelection(world);
        }
        if (lassoPath != null && lassoPath.size() > 1) {
            paintLasso(world);
        }
        world.dispose();

        paintGraphClose(g2);
        if (tool == Tool.ERASER && eraserAt != null) {
            int r = eraserRadius();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(1.2f));
            g2.setColor(eraserMode == EraserMode.PRECISE ? ERASER_OUTLINE : STROKE_ERASER_OUTLINE);
            g2.draw(new Ellipse2D.Double(eraserAt.x - r, eraserAt.y - r, 2 * r, 2 * r));
        }
        g2.dispose();
    }

    /** The graph card in world coordinates: a soft shadow, a white rounded card, the picture inside. */
    private void paintGraphCard(Graphics2D world) {
        if (graphCard == null) {
            return;
        }
        Graphics2D g = (Graphics2D) world.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double unit = 1 / scale;  // one screen pixel in world units
        double arc = 16 * unit;
        for (int i = 1; i <= 5; i++) {
            g.setColor(new Color(15, 23, 42, 7));
            g.fill(new RoundRectangle2D.Double(graphCard.getX() - i * 0.6 * unit, graphCard.getY() + i * 0.8 * unit,
                    graphCard.getWidth() + i * 1.2 * unit, graphCard.getHeight() + i * 0.6 * unit, arc + i * unit, arc + i * unit));
        }
        RoundRectangle2D card = new RoundRectangle2D.Double(graphCard.getX(), graphCard.getY(),
                graphCard.getWidth(), graphCard.getHeight(), arc, arc);
        g.setColor(Color.WHITE);
        g.fill(card);

        Graphics2D picture = (Graphics2D) g.create();
        picture.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        picture.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        picture.translate(graphCard.getX() + graphPadding, graphCard.getY() + graphPadding);
        picture.scale((graphCard.getWidth() - 2 * graphPadding) / graphImage.getWidth(),
                (graphCard.getHeight() - 2 * graphPadding) / graphImage.getHeight());
        picture.drawImage(graphImage, 0, 0, null);
        picture.dispose();

        g.setStroke(new BasicStroke((float) unit));
        g.setColor(GRAPH_CARD_BORDER);
        g.draw(card);
        g.dispose();
    }

    /** The card's × button, on screen so it stays the same size at every zoom. */
    private void paintGraphClose(Graphics2D g2) {
        Point2D c = getGraphCloseOnScreen();
        if (c == null) {
            return;
        }
        Graphics2D g = (Graphics2D) g2.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int r = GRAPH_CLOSE_RADIUS;
        Ellipse2D circle = new Ellipse2D.Double(c.getX() - r, c.getY() - r, 2 * r, 2 * r);
        g.setColor(overGraphClose ? new Color(254, 226, 226) : new Color(255, 255, 255, 235));
        g.fill(circle);
        g.setStroke(new BasicStroke(1f));
        g.setColor(GRAPH_CARD_BORDER);
        g.draw(circle);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(overGraphClose ? new Color(185, 28, 28) : new Color(71, 85, 105));
        double s = 3.8;
        g.draw(new java.awt.geom.Line2D.Double(c.getX() - s, c.getY() - s, c.getX() + s, c.getY() + s));
        g.draw(new java.awt.geom.Line2D.Double(c.getX() - s, c.getY() + s, c.getX() + s, c.getY() - s));
        g.dispose();
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
        for (CanvasAnswer a : answerPreview()) {
            Rectangle2D box = a.getBox();
            Graphics2D halo = (Graphics2D) world.create();
            halo.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            halo.setColor(SELECTION_HALO);
            halo.fill(new RoundRectangle2D.Double(box.getX() - 4, box.getY() - 4, box.getWidth() + 8, box.getHeight() + 8, 8, 8));
            halo.dispose();
            drawAnswer(world, a);
        }

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

    private void paintAnswers(Graphics2D world) {
        for (CanvasAnswer a : getAnswers()) {
            drawAnswer(world, a);
        }
    }

    private static void drawAnswer(Graphics2D world, CanvasAnswer a) {
        Graphics2D g = (Graphics2D) world.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(a.isError() ? ANSWER_ERROR : INK_COLOR);
        AnswerFont.draw(g, a.getText(), a.getFontSize(), a.getX(), a.getY());
        g.dispose();
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
