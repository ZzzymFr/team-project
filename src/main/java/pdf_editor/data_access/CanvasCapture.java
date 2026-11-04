package pdf_editor.data_access;

import view.DrawingCanvas;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

public final class CanvasCapture {
    private final DrawingCanvas source;
    public CanvasCapture(DrawingCanvas source) { this.source = source; }

    public BufferedImage overview() {
        Rectangle bounds = source.getAllInkBounds();
        Rectangle2D graph = source.getGraphCardOnScreen();
        Rectangle screen = bounds == null ? null : source.worldToScreen(bounds).getBounds();
        if (graph != null) {
            if (screen == null) screen = graph.getBounds();
            else screen.add(graph.getBounds());
        }
        if (screen == null || screen.isEmpty()) return new BufferedImage(640, 440, BufferedImage.TYPE_INT_ARGB);
        screen.grow(24, 24);
        double scale = Math.min(1, Math.min(4096.0 / screen.width, 4096.0 / screen.height));
        BufferedImage result = new BufferedImage(Math.max(1, (int) Math.ceil(screen.width * scale)),
                Math.max(1, (int) Math.ceil(screen.height * scale)), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.scale(scale, scale);
        g.translate(-screen.x, -screen.y);
        source.paintContent(g);
        g.dispose();
        return result;
    }

}
