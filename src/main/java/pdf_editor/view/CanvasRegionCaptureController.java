package pdf_editor.view;

import pdf_editor.use_case.CanvasRegionSnapshot;
import view.DrawingCanvas;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.function.Consumer;

/** Temporary screenshot overlay; does not touch stroke selection or the pen/eraser state. */
public final class CanvasRegionCaptureController {
    private CanvasRegionCaptureController() { }

    public static void begin(JComponent canvasArea, DrawingCanvas canvas, Consumer<BufferedImage> completed) {
        Objects.requireNonNull(canvasArea);
        Objects.requireNonNull(canvas);
        Objects.requireNonNull(completed);
        // The event-catching overlay stops the underlying canvas from drawing extra pen strokes.
        CaptureOverlay overlay = new CaptureOverlay(canvasArea, canvas, completed);
        canvasArea.add(overlay, 0);
        overlay.setBounds(0, 0, canvasArea.getWidth(), canvasArea.getHeight());
        canvasArea.revalidate();
        canvasArea.repaint();
    }

    private static final class CaptureOverlay extends JComponent {
        private final JComponent area;
        private final DrawingCanvas canvas;
        private final Consumer<BufferedImage> completed;
        private Point start;
        private Point end;
        private final ComponentAdapter resizing;
        private boolean finished;

        CaptureOverlay(JComponent area, DrawingCanvas canvas, Consumer<BufferedImage> completed) {
            this.area = area;
            this.canvas = canvas;
            this.completed = completed;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            setToolTipText("Drag a screenshot rectangle; right-click or Esc to cancel");
            resizing = new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent e) { setSize(area.getSize()); }
            };
            area.addComponentListener(resizing);
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) { close(); return; }
                    if (SwingUtilities.isLeftMouseButton(e)) start = end = e.getPoint();
                }
                @Override public void mouseDragged(MouseEvent e) {
                    if (start != null) { end = e.getPoint(); repaint(); }
                }
                @Override public void mouseReleased(MouseEvent e) {
                    if (start == null || !SwingUtilities.isLeftMouseButton(e)) return;
                    end = e.getPoint();
                    Rectangle selected = rectangle(start, end);
                    // Convert overlay coordinates to canvas coordinates, not world/ink coordinates.
                    Rectangle inCanvas = SwingUtilities.convertRectangle(CaptureOverlay.this, selected, canvas);
                    close();
                    try {
                        BufferedImage shot = CanvasRegionSnapshot.crop(canvas.toImage(), inCanvas);
                        if (CanvasRegionSnapshot.hasVisibleInk(shot)) completed.accept(shot);
                    } catch (IllegalArgumentException ignored) { /* no ink / too small: no card */ }
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            registerKeyboardAction(e -> close(), javax.swing.KeyStroke.getKeyStroke("ESCAPE"),
                    WHEN_IN_FOCUSED_WINDOW);
        }
        private void close() {
            if (finished) return;
            finished = true;
            area.removeComponentListener(resizing);
            area.remove(this);
            area.revalidate();
            area.repaint();
        }
        private static Rectangle rectangle(Point a, Point b) {
            int x = Math.min(a.x, b.x), y = Math.min(a.y, b.y);
            return new Rectangle(x, y, Math.abs(a.x - b.x), Math.abs(a.y - b.y));
        }
        @Override protected void paintComponent(Graphics g) {
            if (start == null || end == null) return;
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Rectangle r = rectangle(start, end);
                g2.setColor(new Color(36, 113, 210, 24)); g2.fill(r);
                g2.setColor(new Color(36, 113, 210));
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER,
                        10, new float[]{6f, 4f}, 0));
                g2.draw(r);
            } finally { g2.dispose(); }
        }
    }
}
