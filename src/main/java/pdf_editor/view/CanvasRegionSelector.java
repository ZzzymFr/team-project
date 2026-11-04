package pdf_editor.view;

import pdf_editor.adapter.CanvasCoordinateMapper;
import pdf_editor.adapter.CanvasRegionExtractor;
import pdf_editor.adapter.CanvasSelectionModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

/** Read-only canvas view: mouse gestures only; selection/extraction lives in adapters. */
public final class CanvasRegionSelector extends JPanel {
    private final BufferedImage source;
    private final CanvasSelectionModel selection = new CanvasSelectionModel();
    private final CanvasRegionExtractor extractor = new CanvasRegionExtractor();
    private double zoom = 1.0;
    private Point panStart;
    private Point panView;
    private JScrollPane scrollPane;

    public CanvasRegionSelector(BufferedImage source) {
        this.source = java.util.Objects.requireNonNull(source);
        updateDimensions();
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isMiddleMouseButton(e) || SwingUtilities.isRightMouseButton(e)) {
                    panStart = e.getPoint();
                    if (scrollPane != null) panView = scrollPane.getViewport().getViewPosition();
                    return;
                }
                if (SwingUtilities.isLeftMouseButton(e)) selection.start(toSource(e.getPoint()));
                repaint();
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (panStart != null && scrollPane != null) {
                    Point position = new Point(panView.x + panStart.x - e.getX(), panView.y + panStart.y - e.getY());
                    position.x = Math.max(0, Math.min(position.x, getWidth() - scrollPane.getViewport().getWidth()));
                    position.y = Math.max(0, Math.min(position.y, getHeight() - scrollPane.getViewport().getHeight()));
                    scrollPane.getViewport().setViewPosition(position);
                    return;
                }
                selection.drag(toSource(e.getPoint())); repaint();
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (panStart != null) { panStart = null; panView = null; return; }
                if (SwingUtilities.isLeftMouseButton(e)) selection.finish(toSource(e.getPoint()));
                repaint();
            }
        };
        addMouseListener(mouse); addMouseMotionListener(mouse);
    }
    public void setFreehand(boolean enabled) { selection.setFreehand(enabled); repaint(); }
    public void clearSelection() { selection.clear(); repaint(); }
    public BufferedImage selectedRegion() {
        Shape shape = selection.shape();
        return shape == null ? null : extractor.extract(source, shape);
    }
    public void attachScrollPane(JScrollPane pane) {
        scrollPane = pane;
        pane.getVerticalScrollBar().setUnitIncrement(38);
        pane.getHorizontalScrollBar().setUnitIncrement(38);
        addMouseWheelListener(e -> {
            if (e.isControlDown()) setZoom(zoom * Math.pow(1.15, -e.getPreciseWheelRotation()));
            else {
                JScrollBar bar = e.isShiftDown() ? pane.getHorizontalScrollBar() : pane.getVerticalScrollBar();
                bar.setValue(bar.getValue() + (int) Math.round(e.getPreciseWheelRotation() * 45));
            }
            e.consume();
        });
    }
    public double zoom() { return zoom; }
    public void setZoom(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Zoom must be finite");
        zoom = Math.max(.2, Math.min(5.0, value));
        updateDimensions(); revalidate(); repaint();
    }
    public void fitToView() {
        if (scrollPane == null) return;
        Dimension extent = scrollPane.getViewport().getExtentSize();
        setZoom(Math.min((double) Math.max(1, extent.width) / source.getWidth(),
                             (double) Math.max(1, extent.height) / source.getHeight()));
    }
    private void updateDimensions() {
        setPreferredSize(new Dimension(Math.max(1, (int) Math.ceil(source.getWidth() * zoom)),
                                       Math.max(1, (int) Math.ceil(source.getHeight() * zoom))));
    }
    private Point toSource(Point point) {
        return CanvasCoordinateMapper.toSource(point, zoom, source.getWidth(), source.getHeight());
    }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setColor(Color.WHITE); g.fillRect(0, 0, getWidth(), getHeight());
            g.scale(zoom, zoom);
            g.drawImage(source, 0, 0, null);
            Shape shape = selection.shape();
            if (shape != null) {
                g.setColor(new Color(25, 105, 220));
                g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        10, new float[]{5, 5}, 0));
                g.draw(shape);
            }
        } finally { g.dispose(); }
    }
}
