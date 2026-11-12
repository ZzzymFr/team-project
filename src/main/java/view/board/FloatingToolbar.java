package view.board;

import view.DrawingCanvas;

import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * Pen / Eraser / Lasso | colours | Undo / Clear, floating over the canvas area. Drag it by the grip
 * on its end; it docks to the middle of the nearest edge (standing up on the left / right) and remembers it.
 */
public final class FloatingToolbar {
    private static final int FRAME_MS = 5;
    private static final float REF_MS = 16f;

    private final DrawingCanvas canvas;
    private final JComponent area;
    private final GlassBar bar;
    private final GlassBar.Item[] tools = new GlassBar.Item[4];
    private final GlassBar.Item pen;
    private final Spring sx = new Spring();
    private final Spring sy = new Spring();
    private final Timer timer = new Timer(FRAME_MS, e -> step());
    private long lastNanos;
    private ToolbarSpot spot = ToolbarSpot.BOTTOM;
    private boolean dragging;
    private Point grab;
    private Point pointer;   // mouse in area coords while dragging

    // area = where the bar floats, same coords as the canvas
    public FloatingToolbar(DrawingCanvas canvas, JComponent area) {
        this.canvas = canvas;
        this.area = area;
        bar = new GlassBar(BoardTheme.font(14f), canvas, canvas::paintContent, canvas::contentVersion);
        bar.grip(new GlassBar.GripListener() {
            @Override
            public void pressed(Point screen) {
                startDrag(screen);
            }

            @Override
            public void dragged(Point screen) {
                dragTo(screen);
            }

            @Override
            public void released(Point screen) {
                drop();
            }
        });
        pen = tools[0] = bar.segment("Pen", Icons::pencil, 1, () -> use(DrawingCanvas.Tool.PEN));
        tools[1] = bar.segment("Eraser", Icons::eraser, 1, () -> use(DrawingCanvas.Tool.ERASER));
        tools[2] = bar.segment("Lasso", Icons::lasso, 1, () -> use(DrawingCanvas.Tool.LASSO));
        tools[3] = bar.segment("Box", Icons::rectangleSelect, 1, () -> use(DrawingCanvas.Tool.RECTANGLE));
        bar.separator();
        GlassBar.Item first = null;
        for (Color c : BoardTheme.SWATCHES) {
            GlassBar.Item[] self = new GlassBar.Item[1];
            self[0] = bar.swatch(c, 2, () -> useColor(self[0].getColor()));
            if (first == null) {
                first = self[0];
            }
        }
        GlassBar.Item[] custom = new GlassBar.Item[1];
        custom[0] = bar.customSwatch(2, () -> {
            Color chosen = JColorChooser.showDialog(area, "Choose Color", canvas.getInkColor());
            if (chosen == null) {
                return false;  // cancelled, keep the old one
            }
            custom[0].setColor(chosen);
            return useColor(chosen);
        });
        bar.separator();
        bar.action("Undo", Icons::undo, canvas::undo, canvas::canUndo);
        bar.action("Clear", Icons::trash, canvas::clear, () -> !canvas.isBlank());
        bar.finish();
        bar.select(tools[canvas.getTool().ordinal()]);
        bar.select(first);
        canvas.setInkColor(first.getColor());

        canvas.addPropertyChangeListener(DrawingCanvas.TOOL_PROPERTY, e -> {
            bar.select(tools[((DrawingCanvas.Tool) e.getNewValue()).ordinal()]);
            bar.repaint();
        });
        canvas.addUserActionListener(() -> SwingUtilities.invokeLater(bar::repaint));  // Undo / Clear greys
        canvas.addStrokeListener(s -> bar.repaint());
        area.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                relayout();
            }
        });
    }

    public JComponent component() {
        return bar;
    }

    public GlassBar bar() {
        return bar;
    }

    public ToolbarSpot getSpot() {
        return spot;
    }

    // jumps there, no animation
    public void setSpot(ToolbarSpot spot) {
        this.spot = spot;
        relayout();
    }

    private boolean use(DrawingCanvas.Tool tool) {
        canvas.setTool(tool);
        return true;
    }

    // picking a colour recolours a lasso pick, else goes back to the pen
    private boolean useColor(Color c) {
        canvas.setInkColor(c);
        if (canvas.recolorSelection(c)) {
            return true;
        }
        if (canvas.getTool() != DrawingCanvas.Tool.PEN) {
            canvas.setTool(DrawingCanvas.Tool.PEN);
            bar.select(pen);
        }
        return true;
    }

    // stands up on the side edges; narrow room: labels off, then squeezes; then to the remembered spot
    public void relayout() {
        if (!dragging) {
            fit(spot);
        }
        if (!dragging && !timer.isRunning()) {
            bar.setLocation(target());
        }
        area.repaint();
    }

    private void fit(ToolbarSpot s) {
        bar.setVertical(s.isVertical());
        bar.fitTo(s.room(area.getWidth(), area.getHeight()));
        bar.setSize(bar.getPreferredSize());
    }

    private Point target() {
        Point cap = spot.capsuleAt(area.getWidth(), area.getHeight(), bar.capsuleWidth(), GlassBar.CAP_H);
        return new Point(cap.x - GlassBar.MARGIN, cap.y - GlassBar.MARGIN);
    }

    // middle of the grip, in bar coords
    private Point gripCenter() {
        int along = GlassBar.MARGIN + GlassBar.PAD + GlassBar.GRIP_W / 2;
        int across = GlassBar.MARGIN + GlassBar.CAP_H / 2;
        return bar.isVertical() ? new Point(across, along) : new Point(along, across);
    }

    private void startDrag(Point screen) {
        dragging = true;
        timer.stop();
        Point at = bar.getLocationOnScreen();
        grab = new Point(screen.x - at.x, screen.y - at.y);
    }

    // follows the mouse; turns upright / flat as soon as a side edge / the top or bottom is closest
    private void dragTo(Point screen) {
        if (!dragging) {
            return;
        }
        Point areaAt = area.getLocationOnScreen();
        pointer = new Point(screen.x - areaAt.x, screen.y - areaAt.y);
        ToolbarSpot live = ToolbarSpot.nearest(pointer, area.getWidth(), area.getHeight());
        if (live.isVertical() != bar.isVertical()) {
            fit(live);
            grab = gripCenter();  // keep the grip under the mouse
        }
        int m = GlassBar.MARGIN;
        int x = pointer.x - grab.x;
        int y = pointer.y - grab.y;
        x = Math.max(-m, Math.min(x, area.getWidth() - bar.getWidth() + m));
        y = Math.max(-m, Math.min(y, area.getHeight() - bar.getHeight() + m));
        bar.setLocation(x, y);
        area.repaint();
    }

    // spring to the closest edge
    private void drop() {
        dragging = false;
        if (pointer != null) {
            spot = ToolbarSpot.nearest(pointer, area.getWidth(), area.getHeight());
        }
        pointer = null;
        Point from = bar.getLocation();
        fit(spot);
        sx.x = from.x;
        sy.x = from.y;
        sx.v = 0;
        sy.v = 0;
        lastNanos = 0;
        timer.start();
    }

    private void step() {
        long now = System.nanoTime();
        float f = lastNanos == 0 ? FRAME_MS / REF_MS
                : Math.max(0.1f, Math.min(2f, (now - lastNanos) / (REF_MS * 1e6f)));
        lastNanos = now;
        Point t = target();
        boolean done = sx.step(t.x, 0.16f, 0.70f, f) & sy.step(t.y, 0.16f, 0.70f, f);
        bar.setLocation(Math.round(sx.x), Math.round(sy.x));
        area.repaint();
        if (done) {
            timer.stop();
            lastNanos = 0;
        }
    }
}
