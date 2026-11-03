package pdf_editor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class CanvasRegionSelector extends JPanel {
    private final BufferedImage source;
    private final Consumer<BufferedImage> onExtract;
    private boolean freehand;
    private final List<Point> points = new ArrayList<>();
    private Point start;
    private double zoom = 1.0;
    private Point panStart;
    private Point panView;
    private JScrollPane scrollPane;
    CanvasRegionSelector(BufferedImage source, Consumer<BufferedImage> onExtract) {
        this.source = source;
        this.onExtract = onExtract;
        setPreferredSize(new Dimension(Math.max(250, source.getWidth()), Math.max(250, source.getHeight())));
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isMiddleMouseButton(e) || SwingUtilities.isRightMouseButton(e)) {
                    panStart = e.getPoint();
                    if (scrollPane != null) panView = scrollPane.getViewport().getViewPosition();
                    return;
                }
                start = toSource(e.getPoint()); points.clear(); points.add(start); repaint();
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (panStart != null && scrollPane != null) {
                    Point v = new Point(panView.x + panStart.x - e.getX(), panView.y + panStart.y - e.getY());
                    v.x = Math.max(0, Math.min(v.x, Math.max(0, getWidth()-scrollPane.getViewport().getWidth())));
                    v.y = Math.max(0, Math.min(v.y, Math.max(0, getHeight()-scrollPane.getViewport().getHeight())));
                    scrollPane.getViewport().setViewPosition(v); return;
                }
                if (start == null) return;
                if (freehand) points.add(toSource(e.getPoint()));
                else { points.clear(); points.add(start); points.add(toSource(e.getPoint())); }
                repaint();
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (panStart != null) { panStart = null; panView = null; return; }
                if (start == null) return;
                if (freehand) points.add(toSource(e.getPoint()));
                else { points.clear(); points.add(start); points.add(toSource(e.getPoint())); }
                start = null; repaint();
            }
        };
        addMouseListener(mouse); addMouseMotionListener(mouse);
    }
    void setFreehand(boolean value) { freehand = value; clearSelection(); }
    void clearSelection() { start = null; points.clear(); repaint(); }
    boolean hasSelection() { return extract() != null; }
    BufferedImage selectedRegion() { return extract(); }
    void attachScrollPane(JScrollPane pane) {
        scrollPane = pane;
        pane.getVerticalScrollBar().setUnitIncrement(38);
        pane.getHorizontalScrollBar().setUnitIncrement(38);
        addMouseWheelListener(e -> {
            if (e.isControlDown()) {
                setZoom(zoom * Math.pow(1.15, -e.getPreciseWheelRotation()));
                e.consume();
            } else if (e.isShiftDown()) {
                JScrollBar bar = pane.getHorizontalScrollBar();
                bar.setValue(bar.getValue() + (int)(e.getPreciseWheelRotation()*45));
                e.consume();
            } else {
                JScrollBar bar = pane.getVerticalScrollBar();
                bar.setValue(bar.getValue() + (int)(e.getPreciseWheelRotation()*45));
                e.consume();
            }
        });
    }
    double zoom() { return zoom; }
    void setZoom(double value) {
        zoom = Math.max(.2, Math.min(5.0, value));
        setPreferredSize(new Dimension((int)Math.ceil(source.getWidth()*zoom),
            (int)Math.ceil(source.getHeight()*zoom)));
        revalidate(); repaint();
    }
    void fitToView() {
        if (scrollPane == null) return;
        Dimension d = scrollPane.getViewport().getExtentSize();
        setZoom(Math.min((double)Math.max(1,d.width)/source.getWidth(),
                             (double)Math.max(1,d.height)/source.getHeight()));
    }
    private Point toSource(Point p) {
        return new Point(Math.max(0, Math.min(source.getWidth(), (int)(p.x/zoom))),
                         Math.max(0, Math.min(source.getHeight(), (int)(p.y/zoom))));
    }
    BufferedImage fullSnapshot() { return copyRegion(new Rectangle(0, 0, source.getWidth(), source.getHeight()), null); }
    private BufferedImage extract() {
        if (points.size() < 2) return null;
        Shape selection;
        if (freehand) {
            if (points.size() < 3) return null;
            Path2D.Double path = new Path2D.Double();
            path.moveTo(points.get(0).x, points.get(0).y);
            for (int i = 1; i < points.size(); i++) path.lineTo(points.get(i).x, points.get(i).y);
            path.closePath(); selection = path;
        } else {
            Point a = points.get(0), b = points.get(1);
            selection = new Rectangle(Math.min(a.x, b.x), Math.min(a.y, b.y),
                    Math.abs(a.x - b.x), Math.abs(a.y - b.y));
        }
        Rectangle bounds = selection.getBounds().intersection(new Rectangle(0, 0, source.getWidth(), source.getHeight()));
        if (bounds.width < 5 || bounds.height < 5) return null;
        return copyRegion(bounds, selection);
    }
    private BufferedImage copyRegion(Rectangle bounds, Shape mask) {
        BufferedImage out = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        if (mask != null) {
            // Both bounds and mask are already expressed in source-image pixels.
            // Applying zoom here a second time shifts the extracted content.
            g.translate(-bounds.x, -bounds.y);
            g.setClip(mask);
            g.drawImage(source, 0, 0, null);
        }
        else g.drawImage(source, 0, 0, bounds.width, bounds.height, bounds.x, bounds.y,
                bounds.x + bounds.width, bounds.y + bounds.height, null);
        g.dispose(); return out;
    }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setColor(Color.WHITE); g.fillRect(0, 0, getWidth(), getHeight());
        g.scale(zoom, zoom);
        g.drawImage(source, 0, 0, null);
        if (points.size() > 1) {
            g.setColor(new Color(25, 105, 220));
            g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    10, new float[]{5, 5}, 0));
            if (freehand) {
                Path2D.Double path = new Path2D.Double();
                path.moveTo(points.get(0).x, points.get(0).y);
                for (int i = 1; i < points.size(); i++) path.lineTo(points.get(i).x, points.get(i).y);
                g.draw(path);
            } else {
                Point a = points.get(0), b = points.get(1);
                g.drawRect(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.abs(a.x-b.x), Math.abs(a.y-b.y));
            }
        }
        g.dispose();
    }
}
